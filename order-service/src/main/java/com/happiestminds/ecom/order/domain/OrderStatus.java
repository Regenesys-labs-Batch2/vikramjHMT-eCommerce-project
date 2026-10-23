package com.happiestminds.ecom.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    CREATED, STOCK_RESERVED, STOCK_UNAVAILABLE, PAYMENT_PENDING, PAYMENT_FAILED,
    COMPENSATION_PENDING, CONFIRMED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> LEGAL = Map.of(
            CREATED, EnumSet.of(STOCK_RESERVED, STOCK_UNAVAILABLE, CANCELLED),
            STOCK_RESERVED, EnumSet.of(PAYMENT_PENDING),
            PAYMENT_PENDING, EnumSet.of(CONFIRMED, PAYMENT_FAILED, COMPENSATION_PENDING),
            PAYMENT_FAILED, EnumSet.of(COMPENSATION_PENDING, CANCELLED),
            COMPENSATION_PENDING, EnumSet.of(CANCELLED),
            CONFIRMED, EnumSet.of(CANCELLED),
            STOCK_UNAVAILABLE, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus target) {
        return LEGAL.get(this).contains(target);
    }
}
