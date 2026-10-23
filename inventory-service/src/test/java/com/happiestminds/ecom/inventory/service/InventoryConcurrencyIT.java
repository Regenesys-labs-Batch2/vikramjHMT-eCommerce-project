package com.happiestminds.ecom.inventory.service;

import com.happiestminds.ecom.inventory.api.dto.ReservationItem;
import com.happiestminds.ecom.inventory.api.dto.ReservationRequest;
import com.happiestminds.ecom.inventory.domain.InventoryStock;
import com.happiestminds.ecom.inventory.repository.InventoryStockRepository;
import com.happiestminds.ecom.inventory.service.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyIT {

    @Autowired
    ReservationService service;
    @Autowired
    InventoryStockRepository stocks;

    @Test
    @DisplayName("AC-INV-02 R-INV-02 exactly one of twenty parallel reservations for the last unit succeeds")
    void shouldAllowExactlyOneReservation_whenTwentyCustomersWantTheLastUnit() throws Exception {
        String productId = "LAST-" + UUID.randomUUID();
        stocks.save(InventoryStock.of(productId, 1, 0));
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String orderRef = "order-" + UUID.randomUUID();
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.reserve(new ReservationRequest(orderRef, List.of(new ReservationItem(productId, 1))));
                    ok.incrementAndGet();
                } catch (ConflictException e) {
                    conflict.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(threads - 1);
        InventoryStock stock = stocks.findById(productId).orElseThrow();
        assertThat(stock.getAvailableQuantity()).isZero();
        assertThat(stock.getReservedQuantity()).isEqualTo(1);
    }
}
