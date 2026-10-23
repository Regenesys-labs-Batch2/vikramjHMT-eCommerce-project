package com.happiestminds.ecom.cart.client;

import com.happiestminds.ecom.cart.service.exception.DependencyUnavailableException;

import java.util.function.Supplier;

final class Retry {

    private Retry() {
    }

    // Only idempotent GETs may use this; a DependencyUnavailableException is retried with a fixed backoff.
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
