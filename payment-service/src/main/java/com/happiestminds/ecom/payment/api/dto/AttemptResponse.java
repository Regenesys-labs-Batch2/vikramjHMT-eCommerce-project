package com.happiestminds.ecom.payment.api.dto;

import com.happiestminds.ecom.payment.domain.PaymentAttempt;

import java.time.Instant;

public record AttemptResponse(int attemptNumber, String outcome, long simulatedDelayMs, Instant attemptedAt) {

    public static AttemptResponse from(PaymentAttempt a) {
        return new AttemptResponse(a.getAttemptNumber(), a.getOutcome(), a.getSimulatedDelayMs(), a.getAttemptedAt());
    }
}
