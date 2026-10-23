package com.happiestminds.ecom.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}-[0-9]{3}", message = "must match AAA-999") String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotBlank String categoryId,
        @NotNull @Valid PriceDto price,
        Boolean active) {
}
