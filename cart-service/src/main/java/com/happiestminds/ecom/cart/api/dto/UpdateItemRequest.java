package com.happiestminds.ecom.cart.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateItemRequest(@NotNull Integer quantity) {
}
