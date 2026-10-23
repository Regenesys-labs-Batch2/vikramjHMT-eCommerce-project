package com.happiestminds.ecom.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(String id, String customerId, List<CartItemResponse> items, BigDecimal subtotal,
                           String discountCode, BigDecimal discountAmount, BigDecimal total, String currency) {
}
