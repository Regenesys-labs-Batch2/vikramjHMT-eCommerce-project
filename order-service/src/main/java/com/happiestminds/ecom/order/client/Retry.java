package com.happiestminds.ecom.order.client;

import com.happiestminds.ecom.order.service.exception.DependencyUnavailableException;

import java.util.function.Supplier;

final class Retry {

    private Retry() {
    }

    // Only for idempotent calls (GET, confirm, release, refund, restock); never for the initial payment POST.
    static <T> T onUnavailable(int retries, long backoffMs, Supplier<T> call) {
        int attempt = 0;
        while (true) {
            try {
                return call.get();
            } catch (DependencyUnavailableException e) {
                if (attempt++ >= retries) {
                    throw e;
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
