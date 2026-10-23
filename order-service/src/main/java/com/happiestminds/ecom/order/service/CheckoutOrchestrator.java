package com.happiestminds.ecom.order.service;

import com.happiestminds.ecom.order.api.dto.CheckoutRequest;
import com.happiestminds.ecom.order.api.dto.OrderResponse;
import com.happiestminds.ecom.order.client.CartClient;
import com.happiestminds.ecom.order.client.InventoryClient;
import com.happiestminds.ecom.order.client.PaymentClient;
import com.happiestminds.ecom.order.domain.Order;
import com.happiestminds.ecom.order.domain.OrderStatus;
import com.happiestminds.ecom.order.repository.OrderRepository;
import com.happiestminds.ecom.order.service.exception.ConflictException;
import com.happiestminds.ecom.order.service.exception.DependencyUnavailableException;
import com.happiestminds.ecom.order.service.exception.IdempotencyConflictException;
import com.happiestminds.ecom.order.service.exception.InvalidStateException;
import com.happiestminds.ecom.order.service.exception.NotFoundException;
import com.happiestminds.ecom.order.service.exception.UnprocessableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

// Orchestrated saga: reserve stock, pay, confirm, with compensation. Deliberately NOT @Transactional: every
// persisted state change is its own short transaction in OrderTransactions and no remote call runs inside one.
@Service
public class CheckoutOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOrchestrator.class);

    public record CheckoutResult(OrderResponse order, boolean created) {
    }

    private final OrderRepository orders;
    private final OrderTransactions tx;
    private final OrderQueryService queries;
    private final CartClient cart;
    private final InventoryClient inventory;
    private final PaymentClient payments;
    private final int pollAttempts;
    private final long pollIntervalMs;

    public CheckoutOrchestrator(OrderRepository orders, OrderTransactions tx, OrderQueryService queries,
                                CartClient cart, InventoryClient inventory, PaymentClient payments,
                                @Value("${ecom.order.reconcile-attempts:4}") int pollAttempts,
                                @Value("${ecom.order.reconcile-interval-ms:1500}") long pollIntervalMs) {
        this.orders = orders;
        this.tx = tx;
        this.queries = queries;
        this.cart = cart;
        this.inventory = inventory;
        this.payments = payments;
        this.pollAttempts = Math.max(1, pollAttempts);
        this.pollIntervalMs = pollIntervalMs;
    }

    public CheckoutResult checkout(String customerId, String idempotencyKey, CheckoutRequest request) {
        String outcome = request == null || request.simulatedPaymentOutcome() == null
                || request.simulatedPaymentOutcome().isBlank() ? null : request.simulatedPaymentOutcome();
        String requestHash = hash(outcome);

        var duplicate = orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (duplicate.isPresent()) {
            return replay(duplicate.get(), requestHash);
        }

        CartClient.CheckoutSummary summary = cart.checkoutSummary();
        validate(summary);

        String orderId;
        try {
            orderId = tx.createOrder(customerId, idempotencyKey, requestHash, summary, outcome);
        } catch (DataIntegrityViolationException sameKeyAtTheSameTime) {
            return replay(orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey).orElseThrow(),
                    requestHash);
        }

        InventoryClient.Reservation reservation;
        try {
            List<InventoryClient.Item> items = summary.items().stream()
                    .map(i -> new InventoryClient.Item(i.productId(), i.quantity())).toList();
            reservation = inventory.reserve(orderId, items);
        } catch (ConflictException insufficientStock) {
            tx.fail(orderId, OrderStatus.STOCK_UNAVAILABLE, "INSUFFICIENT_STOCK");
            throw insufficientStock;
        } catch (DependencyUnavailableException inventoryDown) {
            // An unacknowledged hold, if any, expires through the reservation TTL.
            tx.fail(orderId, OrderStatus.CANCELLED, "INVENTORY_UNAVAILABLE");
            throw inventoryDown;
        }
        tx.markReserved(orderId, reservation.reservationId());
        tx.transition(orderId, OrderStatus.PAYMENT_PENDING, "payment requested");

        PaymentClient.Payment payment;
        try {
            payment = payments.create(orderId, customerId, summary.total(), summary.currency(), outcome);
        } catch (DependencyUnavailableException paymentUnreachable) {
            log.warn("Payment call failed for order {}; left in PAYMENT_PENDING for reconciliation", orderId);
            throw paymentUnreachable;
        }
        return new CheckoutResult(settle(orderId, reservation.reservationId(), payment), true);
    }

    public OrderResponse recover(String orderId) {
        Order order = load(orderId);
        switch (order.getStatus()) {
            case PAYMENT_PENDING -> reconcilePayment(order);
            case COMPENSATION_PENDING -> resumeCompensation(order);
            case CONFIRMED -> {
                if (!order.isCartCheckedOut()) {
                    markCartCheckedOut(order);
                }
            }
            default -> {
                // nothing to resume
            }
        }
        return queries.view(orderId);
    }

    public OrderResponse cancel(String orderId, String callerId, boolean privileged) {
        Order order = load(orderId);
        OrderQueryService.requireOwnerOrPrivileged(order.getCustomerId(), callerId, privileged);
        switch (order.getStatus()) {
            case CONFIRMED -> {
                // Refund first, then restock; both calls are idempotent so a failed cancel can simply be repeated.
                payments.refund(order.getPaymentRef(), "ORDER_CANCELLED");
                for (OrderResponse.ItemResponse item : queries.view(orderId).items()) {
                    inventory.restock(item.productId(), item.quantity(), "cancel-" + orderId);
                }
                tx.transition(orderId, OrderStatus.CANCELLED, "cancelled by " + (privileged ? "admin" : "customer"));
            }
            case PAYMENT_FAILED -> {
                tx.transition(orderId, OrderStatus.COMPENSATION_PENDING, "release requested");
                resumeCompensation(load(orderId));
            }
            case COMPENSATION_PENDING -> resumeCompensation(order);
            default -> throw new InvalidStateException("INVALID_ORDER_STATE",
                    "An order in state " + order.getStatus() + " cannot be cancelled");
        }
        return queries.view(orderId);
    }

    private OrderResponse settle(String orderId, String reservationId, PaymentClient.Payment payment) {
        if ("SUCCEEDED".equals(payment.status())) {
            return complete(orderId, reservationId, payment.id());
        }
        if ("DECLINED".equals(payment.status())) {
            return compensate(orderId, reservationId,
                    payment.failureReason() == null ? "PAYMENT_DECLINED" : payment.failureReason());
        }
        tx.setPaymentRef(orderId, payment.id());
        log.warn("Payment {} for order {} is {}; reconciliation will decide", payment.id(), orderId, payment.status());
        throw new DependencyUnavailableException("Payment is still being processed");
    }

    private OrderResponse complete(String orderId, String reservationId, String paymentId) {
        try {
            inventory.confirm(reservationId);
        } catch (InvalidStateException reservationLost) {
            log.warn("Reservation {} is gone after payment {}; refunding order {}", reservationId, paymentId, orderId);
            tx.setPaymentRef(orderId, paymentId);
            tx.fail(orderId, OrderStatus.COMPENSATION_PENDING, "RESERVATION_LOST");
            refundAndCancel(orderId, paymentId);
            return queries.view(orderId);
        } catch (DependencyUnavailableException confirmFailed) {
            tx.setPaymentRef(orderId, paymentId);
            log.warn("Could not confirm reservation for order {}; left in PAYMENT_PENDING", orderId);
            throw confirmFailed;
        }
        tx.confirm(orderId, paymentId);
        markCartCheckedOut(load(orderId));
        return queries.view(orderId);
    }

    private OrderResponse compensate(String orderId, String reservationId, String reason) {
        tx.fail(orderId, OrderStatus.PAYMENT_FAILED, reason);
        tx.transition(orderId, OrderStatus.COMPENSATION_PENDING, "release requested");
        releaseAndCancel(orderId, reservationId);
        return queries.view(orderId);
    }

    private void reconcilePayment(Order order) {
        PaymentClient.Payment payment = null;
        for (int attempt = 1; attempt <= pollAttempts; attempt++) {
            // Same Idempotency-Key (the order id) returns the original transaction; never a second charge.
            payment = payments.create(order.getId(), order.getCustomerId(), order.getTotalAmount(),
                    order.getCurrency(), order.getSimulatedPaymentOutcome());
            if (!"INITIATED".equals(payment.status()) || attempt == pollAttempts) {
                break;
            }
            pause(pollIntervalMs);
        }
        if ("SUCCEEDED".equals(payment.status())) {
            complete(order.getId(), order.getReservationRef(), payment.id());
        } else if ("DECLINED".equals(payment.status())) {
            compensate(order.getId(), order.getReservationRef(),
                    payment.failureReason() == null ? "PAYMENT_DECLINED" : payment.failureReason());
        } else {
            log.info("Payment {} for order {} is still {}", payment.id(), order.getId(), payment.status());
        }
    }

    private void resumeCompensation(Order order) {
        if ("RESERVATION_LOST".equals(order.getFailureReason()) && order.getPaymentRef() != null) {
            refundAndCancel(order.getId(), order.getPaymentRef());
        } else if (order.getReservationRef() != null) {
            releaseAndCancel(order.getId(), order.getReservationRef());
        }
    }

    // Idempotent for EXPIRED reservations too; failure keeps the order in COMPENSATION_PENDING for the retry job.
    private void releaseAndCancel(String orderId, String reservationId) {
        try {
            InventoryClient.Reservation released = inventory.release(reservationId);
            if ("RELEASED".equals(released.status()) || "EXPIRED".equals(released.status())) {
                tx.transition(orderId, OrderStatus.CANCELLED, "stock released");
            } else {
                log.warn("Release of {} returned {}; order {} stays COMPENSATION_PENDING", reservationId,
                        released.status(), orderId);
            }
        } catch (DependencyUnavailableException e) {
            log.warn("Release failed for order {}; stays COMPENSATION_PENDING", orderId);
        }
    }

    private void refundAndCancel(String orderId, String paymentId) {
        try {
            payments.refund(paymentId, "RESERVATION_LOST");
            tx.transition(orderId, OrderStatus.CANCELLED, "payment refunded");
        } catch (DependencyUnavailableException e) {
            log.warn("Refund failed for order {}; stays COMPENSATION_PENDING", orderId);
        }
    }

    // Best effort (R-ORD-09): a failure leaves cartCheckedOut=false and the recovery job re-sends it.
    private void markCartCheckedOut(Order order) {
        try {
            cart.markCheckedOut(order.getCartRef());
            tx.markCartCheckedOut(order.getId());
        } catch (RuntimeException e) {
            log.warn("Cart {} was not marked checked-out for order {}; recovery will retry", order.getCartRef(),
                    order.getId());
        }
    }

    private CheckoutResult replay(Order existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key was already used with a different request body");
        }
        return new CheckoutResult(queries.view(existing.getId()), false);
    }

    private static void validate(CartClient.CheckoutSummary summary) {
        if (summary == null || summary.items() == null || summary.items().isEmpty()) {
            throw new UnprocessableException("EMPTY_CART", "Cart is empty");
        }
        BigDecimal lines = summary.items().stream().map(CartClient.Item::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean consistent = lines.compareTo(summary.subtotal()) == 0
                && summary.subtotal().subtract(summary.discountAmount()).max(BigDecimal.ZERO)
                .compareTo(summary.total()) == 0;
        if (!consistent) {
            throw new DependencyUnavailableException("Cart returned inconsistent totals");
        }
    }

    private Order load(String orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String hash(String outcome) {
        String canonical = "simulatedPaymentOutcome=" + (outcome == null ? "" : outcome);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
