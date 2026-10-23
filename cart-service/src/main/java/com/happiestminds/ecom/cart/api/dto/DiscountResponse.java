package com.happiestminds.ecom.cart.api.dto;

import com.happiestminds.ecom.cart.domain.DiscountRule;

import java.math.BigDecimal;
import java.time.Instant;

public record DiscountResponse(String id, String code, String type, BigDecimal value, BigDecimal minimumOrderTotal,
                               boolean active, Instant validFrom, Instant validTo) {

    public static DiscountResponse from(DiscountRule r) {
        return new DiscountResponse(r.getId(), r.getCode(), r.getType().name(), r.getDiscountValue(),
                r.getMinimumOrderTotal(), r.isActive(), r.getValidFrom(), r.getValidTo());
    }
}
