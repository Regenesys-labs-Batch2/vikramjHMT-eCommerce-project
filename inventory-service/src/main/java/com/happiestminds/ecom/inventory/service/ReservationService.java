package com.happiestminds.ecom.inventory.service;

import com.happiestminds.ecom.inventory.api.ApiError;
import com.happiestminds.ecom.inventory.api.dto.ReservationItem;
import com.happiestminds.ecom.inventory.api.dto.ReservationRequest;
import com.happiestminds.ecom.inventory.api.dto.ReservationResponse;
import com.happiestminds.ecom.inventory.domain.ReservationStatus;
import com.happiestminds.ecom.inventory.domain.StockReservation;
import com.happiestminds.ecom.inventory.repository.InventoryStockRepository;
import com.happiestminds.ecom.inventory.repository.StockReservationRepository;
import com.happiestminds.ecom.inventory.service.exception.ConflictException;
import com.happiestminds.ecom.inventory.service.exception.InvalidStateException;
import com.happiestminds.ecom.inventory.service.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// No @Transactional here: each operation runs in its own short TransactionTemplate so conflicts can be retried.
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    public record Result(ReservationResponse body, boolean created) {
    }

    private final InventoryStockRepository stocks;
    private final StockReservationRepository reservations;
    private final TransactionTemplate tx;
    private final long ttlMinutes;

    public ReservationService(InventoryStockRepository stocks, StockReservationRepository reservations,
                              PlatformTransactionManager txManager,
                              @Value("${ecom.inventory.reservation-ttl-minutes:15}") long ttlMinutes) {
        this.stocks = stocks;
        this.reservations = reservations;
        this.tx = new TransactionTemplate(txManager);
        this.ttlMinutes = ttlMinutes;
    }

    public Result reserve(ReservationRequest request) {
        List<StockReservation> existing = reservations.findByOrderRef(request.orderRef());
        if (!existing.isEmpty()) {
            return new Result(ReservationResponse.from(existing), false);
        }
        try {
            return tx.execute(status -> doReserve(request));
        } catch (DataIntegrityViolationException sameOrderRefAtTheSameTime) {
            return new Result(ReservationResponse.from(reservations.findByOrderRef(request.orderRef())), false);
        }
    }

    public ReservationResponse confirm(String reservationId) {
        return withOneRetry(() -> tx.execute(status -> doConfirm(reservationId)));
    }

    public ReservationResponse release(String reservationId) {
        return withOneRetry(() -> tx.execute(status -> doRelease(reservationId)));
    }

    public ReservationResponse get(String reservationId) {
        return tx.execute(status -> ReservationResponse.from(group(reservationId)));
    }

    @Scheduled(fixedDelayString = "${ecom.inventory.expiry-interval-ms:30000}")
    public void expireOverdue() {
        for (String id : reservations.findIdsByStatusAndExpiresAtBefore(ReservationStatus.PENDING, Instant.now())) {
            try {
                tx.executeWithoutResult(status -> expireOne(id));
            } catch (OptimisticLockingFailureException concurrentChange) {
                log.info("Reservation {} changed while expiring; skipped", id);
            }
        }
    }

    private Result doReserve(ReservationRequest request) {
        List<StockReservation> existing = reservations.findByOrderRef(request.orderRef());
        if (!existing.isEmpty()) {
            return new Result(ReservationResponse.from(existing), false);
        }
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (ReservationItem item : request.items()) {
            wanted.merge(item.productId(), item.quantity(), Integer::sum);
        }
        Instant now = Instant.now();
        List<StockReservation> created = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            // Atomic conditional update: the row count, not a prior read, decides whether stock was available.
            if (stocks.reserve(entry.getKey(), entry.getValue(), now) == 0) {
                throw new ConflictException("INSUFFICIENT_STOCK", "Requested quantity exceeds available stock",
                        List.of(new ApiError.FieldIssue("items.productId", entry.getKey())));
            }
            created.add(reservations.save(
                    StockReservation.pending(request.orderRef(), entry.getKey(), entry.getValue(), ttlMinutes)));
        }
        reservations.flush();
        log.info("Reservation created orderRef={} items={}", request.orderRef(), created.size());
        return new Result(ReservationResponse.from(created), true);
    }

    private ReservationResponse doConfirm(String reservationId) {
        List<StockReservation> rows = group(reservationId);
        Instant now = Instant.now();
        for (StockReservation r : rows) {
            switch (r.getStatus()) {
                case CONFIRMED -> {
                    // idempotent
                }
                case PENDING -> {
                    r.markStatus(ReservationStatus.CONFIRMED);
                    requireUpdated(stocks.confirm(r.getProductId(), r.getQuantity(), now), r);
                }
                default -> throw new InvalidStateException("INVALID_RESERVATION_STATE",
                        "Reservation is " + r.getStatus() + " and cannot be confirmed");
            }
        }
        log.info("Reservation confirmed id={}", reservationId);
        return ReservationResponse.from(rows);
    }

    private ReservationResponse doRelease(String reservationId) {
        List<StockReservation> rows = group(reservationId);
        Instant now = Instant.now();
        for (StockReservation r : rows) {
            switch (r.getStatus()) {
                case RELEASED, EXPIRED -> {
                    // idempotent: stock was already returned
                }
                case PENDING -> {
                    r.markStatus(ReservationStatus.RELEASED);
                    requireUpdated(stocks.release(r.getProductId(), r.getQuantity(), now), r);
                }
                default -> throw new InvalidStateException("INVALID_RESERVATION_STATE",
                        "Reservation is " + r.getStatus() + " and cannot be released");
            }
        }
        log.info("Reservation released id={}", reservationId);
        return ReservationResponse.from(rows);
    }

    private void expireOne(String id) {
        StockReservation r = reservations.findById(id).orElse(null);
        if (r == null || r.getStatus() != ReservationStatus.PENDING) {
            return;
        }
        r.markStatus(ReservationStatus.EXPIRED);
        requireUpdated(stocks.release(r.getProductId(), r.getQuantity(), Instant.now()), r);
        log.warn("Reservation expired id={} orderRef={}", r.getId(), r.getOrderRef());
    }

    private List<StockReservation> group(String reservationId) {
        StockReservation row = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));
        return reservations.findByOrderRef(row.getOrderRef());
    }

    private static void requireUpdated(int updatedRows, StockReservation r) {
        if (updatedRows == 0) {
            throw new IllegalStateException("Stock counters inconsistent for product " + r.getProductId());
        }
    }

    private static <T> T withOneRetry(Supplier<T> action) {
        try {
            return action.get();
        } catch (OptimisticLockingFailureException first) {
            return action.get();
        }
    }
}
