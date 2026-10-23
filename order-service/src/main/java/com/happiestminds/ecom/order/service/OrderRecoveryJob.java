package com.happiestminds.ecom.order.service;

import com.happiestminds.ecom.order.domain.Order;
import com.happiestminds.ecom.order.domain.OrderStatus;
import com.happiestminds.ecom.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Resumes orders stuck in a pending state or whose cart was not marked checked-out. Each order is recovered from
// its persisted state, so running the job repeatedly is safe.
@Component
public class OrderRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderRecoveryJob.class);

    private final OrderRepository orders;
    private final CheckoutOrchestrator orchestrator;
    private final Duration minAge;

    public OrderRecoveryJob(OrderRepository orders, CheckoutOrchestrator orchestrator,
                            @Value("${ecom.order.recovery-min-age-ms:30000}") long minAgeMs) {
        this.orders = orders;
        this.orchestrator = orchestrator;
        this.minAge = Duration.ofMillis(minAgeMs);
    }

    @Scheduled(fixedDelayString = "${ecom.order.recovery-interval-ms:60000}",
            initialDelayString = "${ecom.order.recovery-interval-ms:60000}")
    public void run() {
        // The age filter keeps the job away from checkouts that are still in flight.
        Instant cutoff = Instant.now().minus(minAge);
        List<Order> stuck = orders.findByStatusInAndUpdatedAtBefore(
                List.of(OrderStatus.PAYMENT_PENDING, OrderStatus.COMPENSATION_PENDING), cutoff);
        List<Order> cartPending = orders.findByStatusAndCartCheckedOutFalseAndUpdatedAtBefore(
                OrderStatus.CONFIRMED, cutoff);
        for (Order order : concat(stuck, cartPending)) {
            try {
                orchestrator.recover(order.getId());
            } catch (RuntimeException e) {
                log.error("Recovery failed for order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    private static List<Order> concat(List<Order> first, List<Order> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
