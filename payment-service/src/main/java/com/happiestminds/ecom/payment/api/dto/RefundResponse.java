package com.happiestminds.ecom.payment.api.dto;

import com.happiestminds.ecom.payment.domain.Refund;

import java.math.BigDecimal;

public record RefundResponse(String refundId, String status, BigDecimal amount, String reason) {

    public static RefundResponse from(Refund r) {
        return new RefundResponse(r.getId(), r.getStatus().name(), r.getAmount(), r.getReason());
    }
}
