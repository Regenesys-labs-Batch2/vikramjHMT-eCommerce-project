package com.happiestminds.ecom.cart.service.pricing;

import com.happiestminds.ecom.cart.domain.DiscountRule;
import com.happiestminds.ecom.cart.domain.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FixedDiscountStrategy implements DiscountStrategy {

    @Override
    public DiscountType type() {
        return DiscountType.FIXED;
    }

    @Override
    public BigDecimal calculate(BigDecimal subtotal, DiscountRule rule) {
        return rule.getDiscountValue().min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }
}
