package com.happiestminds.ecom.order.api.dto;

import com.happiestminds.ecom.order.domain.Order;
import com.happiestminds.ecom.order.domain.OrderItem;
import com.happiestminds.ecom.order.domain.OrderStatusHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(String id, String customerId, String status, List<ItemResponse> items, BigDecimal subtotal,
                            BigDecimal discountAmount, BigDecimal totalAmount, String currency, String reservationRef,
                            String paymentRef, String failureReason, Instant createdAt,
                            List<HistoryResponse> statusHistory) {

    public record ItemResponse(String productId, String sku, String productName, int quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal) {
        static ItemResponse from(OrderItem i) {
            return new ItemResponse(i.getProductId(), i.getSku(), i.getProductName(), i.getQuantity(),
                    i.getUnitPriceSnapshot(), i.getLineTotal());
        }
    }

    public record HistoryResponse(String fromStatus, String toStatus, String reason, Instant changedAt) {
        static HistoryResponse from(OrderStatusHistory h) {
            return new HistoryResponse(h.getFromStatus() == null ? null : h.getFromStatus().name(),
                    h.getToStatus().name(), h.getReason(), h.getChangedAt());
        }
    }

    // Must be called inside a transaction: items and history are lazy collections.
    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus().name(),
                o.getItems().stream().map(ItemResponse::from).toList(), o.getSubtotal(), o.getDiscountAmount(),
                o.getTotalAmount(), o.getCurrency(), o.getReservationRef(), o.getPaymentRef(), o.getFailureReason(),
                o.getCreatedAt(), o.getHistory().stream().map(HistoryResponse::from).toList());
    }
}
