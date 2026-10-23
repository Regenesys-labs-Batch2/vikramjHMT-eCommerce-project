package com.happiestminds.ecom.payment.service;

import com.happiestminds.ecom.payment.api.dto.PaymentRequest;
import com.happiestminds.ecom.payment.api.dto.PaymentResponse;
import com.happiestminds.ecom.payment.api.dto.RefundRequest;
import com.happiestminds.ecom.payment.api.dto.RefundResponse;
import com.happiestminds.ecom.payment.domain.PaymentAttempt;
import com.happiestminds.ecom.payment.domain.PaymentStatus;
import com.happiestminds.ecom.payment.domain.PaymentTransaction;
import com.happiestminds.ecom.payment.domain.Refund;
import com.happiestminds.ecom.payment.domain.RefundStatus;
import com.happiestminds.ecom.payment.domain.SimulatedOutcome;
import com.happiestminds.ecom.payment.repository.PaymentAttemptRepository;
import com.happiestminds.ecom.payment.repository.PaymentTransactionRepository;
import com.happiestminds.ecom.payment.repository.RefundRepository;
import com.happiestminds.ecom.payment.service.exception.BadRequestException;
import com.happiestminds.ecom.payment.service.exception.ForbiddenException;
import com.happiestminds.ecom.payment.service.exception.IdempotencyConflictException;
import com.happiestminds.ecom.payment.service.exception.InvalidStateException;
import com.happiestminds.ecom.payment.service.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

// create() is deliberately not @Transactional: INITIATED is committed first, the simulated delay runs
// with no transaction open, and the outcome is written in a second short transaction.
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public record Result(PaymentResponse body, boolean created) {
    }

    public record RefundResult(RefundResponse body, boolean created) {
    }

    private final PaymentTransactionRepository payments;
    private final PaymentAttemptRepository attempts;
    private final RefundRepository refunds;
    private final PaymentSimulator simulator;
    private final TransactionTemplate tx;
    private final long simulatedTimeoutMs;

    public PaymentService(PaymentTransactionRepository payments, PaymentAttemptRepository attempts,
                          RefundRepository refunds, PaymentSimulator simulator, PlatformTransactionManager txManager,
                          @Value("${ecom.payment.simulated-timeout-ms:5000}") long simulatedTimeoutMs) {
        this.payments = payments;
        this.attempts = attempts;
        this.refunds = refunds;
        this.simulator = simulator;
        this.tx = new TransactionTemplate(txManager);
        this.simulatedTimeoutMs = simulatedTimeoutMs;
    }

    public Result create(String idempotencyKey, PaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("MISSING_IDEMPOTENCY_KEY", "A valid Idempotency-Key header is required");
        }
        String hash = requestHash(request);
        Optional<PaymentTransaction> existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        PaymentTransaction initiated = PaymentTransaction.initiated(idempotencyKey, hash, request.orderId(),
                request.customerId(), request.amount().setScale(2, RoundingMode.HALF_UP), request.currency());
        PaymentTransaction saved;
        try {
            saved = tx.execute(status -> payments.saveAndFlush(initiated));
        } catch (DataIntegrityViolationException sameKeyAtTheSameTime) {
            return replay(payments.findByIdempotencyKey(idempotencyKey).orElseThrow(), hash);
        }

        SimulatedOutcome outcome = simulator.decide(request.simulatedOutcome(), request.amount());
        long delayMs = outcome == SimulatedOutcome.TIMEOUT ? simulatedTimeoutMs : 0;
        if (delayMs > 0) {
            simulator.sleep(delayMs);
        }
        PaymentTransaction done = tx.execute(status -> {
            PaymentTransaction fresh = payments.findById(saved.getId()).orElseThrow();
            // A simulated TIMEOUT still ends as SUCCEEDED: the caller gave up, the payment did not.
            if (outcome == SimulatedOutcome.DECLINED) {
                fresh.record(PaymentStatus.DECLINED, "CARD_DECLINED");
            } else {
                fresh.record(PaymentStatus.SUCCEEDED, null);
            }
            attempts.save(new PaymentAttempt(fresh, 1, outcome.name(), delayMs));
            return payments.save(fresh);
        });
        log.info("Payment {} for order {} finished as {}", done.getId(), done.getOrderId(), done.getStatus());
        return new Result(toResponse(done), true);
    }

    public PaymentResponse get(String paymentId, String callerId, boolean privileged) {
        PaymentTransaction payment = payments.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "Payment not found"));
        requireVisible(payment, callerId, privileged);
        return toResponse(payment);
    }

    public List<PaymentResponse> listByOrder(String orderId, String callerId, boolean privileged) {
        return payments.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(p -> privileged || p.getCustomerId().equals(callerId))
                .map(this::toResponse)
                .toList();
    }

    public RefundResult refund(String paymentId, RefundRequest request) {
        try {
            return tx.execute(status -> doRefund(paymentId, request));
        } catch (DataIntegrityViolationException concurrentRefund) {
            return new RefundResult(RefundResponse.from(refunds.findByPaymentId(paymentId).orElseThrow()), false);
        }
    }

    private RefundResult doRefund(String paymentId, RefundRequest request) {
        PaymentTransaction payment = payments.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "Payment not found"));
        // The existing refund is looked up first: by now the payment is REFUNDED, not SUCCEEDED.
        Optional<Refund> existing = refunds.findByPaymentId(paymentId);
        if (existing.isPresent()) {
            return new RefundResult(RefundResponse.from(existing.get()), false);
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new InvalidStateException("INVALID_PAYMENT_STATE",
                    "Only a SUCCEEDED payment can be refunded; this one is " + payment.getStatus());
        }
        BigDecimal amount = request == null || request.amount() == null
                ? payment.getAmount() : request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("VALIDATION_ERROR", "Refund amount exceeds the payment amount");
        }
        String reason = request == null || request.reason() == null ? "REFUND" : request.reason();
        Refund refund = refunds.save(new Refund(payment, amount, RefundStatus.COMPLETED, reason));
        payment.record(PaymentStatus.REFUNDED, payment.getFailureReason());
        payments.save(payment);
        refunds.flush();
        log.info("Payment {} refunded amount={} reason={}", paymentId, amount, reason);
        return new RefundResult(RefundResponse.from(refund), true);
    }

    private Result replay(PaymentTransaction existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new IdempotencyConflictException("IDEMPOTENCY_CONFLICT",
                    "Idempotency key was already used with different request details");
        }
        return new Result(toResponse(existing), false);
    }

    private static void requireVisible(PaymentTransaction payment, String callerId, boolean privileged) {
        if (!privileged && !payment.getCustomerId().equals(callerId)) {
            throw new ForbiddenException("You can only read your own payments");
        }
    }

    private PaymentResponse toResponse(PaymentTransaction payment) {
        List<PaymentAttempt> rows = attempts.findByPaymentIdOrderByAttemptNumberAsc(payment.getId());
        return PaymentResponse.from(payment, rows);
    }

    static String requestHash(PaymentRequest request) {
        String canonical = request.orderId() + "|" + request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "|" + request.currency();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
