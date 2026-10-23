package com.happiestminds.ecom.payment.api.dto;

import com.happiestminds.ecom.payment.domain.PaymentAttempt;
import com.happiestminds.ecom.payment.domain.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentResponse(String id, String orderId, String customerId, BigDecimal amount, String currency,
                              String status, String failureReason, Instant createdAt,
                              List<AttemptResponse> attempts) {

    public static PaymentResponse from(PaymentTransaction t, List<PaymentAttempt> attempts) {
        return new PaymentResponse(t.getId(), t.getOrderId(), t.getCustomerId(), t.getAmount(), t.getCurrency(),
                t.getStatus().name(), t.getFailureReason(), t.getCreatedAt(),
                attempts.stream().map(AttemptResponse::from).toList());
    }
}
