package com.happiestminds.ecom.payment.api.dto;

import com.happiestminds.ecom.payment.domain.SimulatedOutcome;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank String orderId,
        @NotBlank String customerId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "INR", message = "must be INR") String currency,
        SimulatedOutcome simulatedOutcome) {
}
