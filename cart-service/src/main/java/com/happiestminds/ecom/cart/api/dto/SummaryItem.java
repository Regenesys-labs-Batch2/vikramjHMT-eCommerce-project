package com.happiestminds.ecom.cart.api.dto;

import java.math.BigDecimal;

public record SummaryItem(String productId, String sku, String productName, int quantity, BigDecimal unitPrice,
                          BigDecimal lineTotal) {
}
