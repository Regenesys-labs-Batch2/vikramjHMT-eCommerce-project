package com.happiestminds.ecom.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ReservationItem(@NotBlank String productId, @Positive int quantity) {
}
