package com.happiestminds.ecom.cart.api.dto;

import com.happiestminds.ecom.cart.domain.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record DiscountRequest(
        @NotBlank @Size(max = 50) String code,
        @NotNull DiscountType type,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal value,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minimumOrderTotal,
        Boolean active,
        Instant validFrom,
        Instant validTo) {
}
