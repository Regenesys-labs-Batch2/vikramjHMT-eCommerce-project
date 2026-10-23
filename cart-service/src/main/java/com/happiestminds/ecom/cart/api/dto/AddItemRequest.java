package com.happiestminds.ecom.cart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Quantity range is checked in the service so it returns INVALID_QUANTITY rather than VALIDATION_ERROR.
public record AddItemRequest(@NotBlank String productId, @NotNull Integer quantity) {
}
