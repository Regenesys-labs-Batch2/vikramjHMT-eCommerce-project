package com.happiestminds.ecom.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceDto(@NotNull BigDecimal amount, @NotBlank String currency) {
}
