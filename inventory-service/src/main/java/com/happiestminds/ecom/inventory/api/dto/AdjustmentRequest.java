package com.happiestminds.ecom.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustmentRequest(@NotNull Integer delta, @NotBlank @Size(max = 200) String reason,
                                @Size(max = 100) String reference) {
}
