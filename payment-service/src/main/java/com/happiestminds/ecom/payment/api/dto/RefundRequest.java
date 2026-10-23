package com.happiestminds.ecom.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundRequest(
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(max = 200) String reason) {
}
