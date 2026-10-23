package com.happiestminds.ecom.payment.service;

import com.happiestminds.ecom.payment.domain.SimulatedOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;

// Deterministic rules so tests are repeatable; no real payment provider is ever called (R-PAY-04).
@Component
public class PaymentSimulator {

    public SimulatedOutcome decide(SimulatedOutcome requested, BigDecimal amount) {
        if (requested != null) {
            return requested;
        }
        int cents = amount.setScale(2, java.math.RoundingMode.HALF_UP).unscaledValue()
                .mod(BigInteger.valueOf(100)).intValue();
        return switch (cents) {
            case 99 -> SimulatedOutcome.DECLINED;
            case 98 -> SimulatedOutcome.TIMEOUT;
            default -> SimulatedOutcome.SUCCESS;
        };
    }

    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
