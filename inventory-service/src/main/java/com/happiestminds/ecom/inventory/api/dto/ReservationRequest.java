package com.happiestminds.ecom.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReservationRequest(@NotBlank String orderRef, @NotEmpty List<@Valid ReservationItem> items) {
}
