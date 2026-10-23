package com.happiestminds.ecom.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutSummaryResponse(String cartId, String customerId, List<SummaryItem> items, BigDecimal subtotal,
                                      String discountCode, BigDecimal discountAmount, BigDecimal total,
                                      String currency) {
}
