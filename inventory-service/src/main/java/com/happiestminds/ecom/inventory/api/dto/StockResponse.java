package com.happiestminds.ecom.inventory.api.dto;

import com.happiestminds.ecom.inventory.domain.InventoryStock;

public record StockResponse(String productId, int availableQuantity, int reservedQuantity, Long version) {

    public static StockResponse from(InventoryStock s) {
        return new StockResponse(s.getProductId(), s.getAvailableQuantity(), s.getReservedQuantity(), s.getVersion());
    }
}
