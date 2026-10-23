package com.happiestminds.ecom.cart.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ApplyDiscountRequest(@NotBlank String code) {
}
