package com.happiestminds.ecom.cart.service.pricing;

import com.happiestminds.ecom.cart.domain.DiscountRule;
import com.happiestminds.ecom.cart.domain.DiscountType;

import java.math.BigDecimal;

// One strategy per DiscountType keeps new rule types from touching existing ones.
public interface DiscountStrategy {

    DiscountType type();

    BigDecimal calculate(BigDecimal subtotal, DiscountRule rule);
}
