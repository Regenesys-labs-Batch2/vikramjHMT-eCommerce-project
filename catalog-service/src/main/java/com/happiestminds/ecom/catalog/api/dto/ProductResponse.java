package com.happiestminds.ecom.catalog.api.dto;

import java.time.Instant;

public record ProductResponse(String id, String sku, String name, String description, String categoryId,
                              String categoryName, boolean active, PriceDto price, Instant createdAt,
                              Instant updatedAt) {
}
