package com.happiestminds.ecom.cart.service.pricing;

import com.happiestminds.ecom.cart.domain.DiscountRule;
import com.happiestminds.ecom.cart.domain.DiscountType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PricingService {

    public record Line(BigDecimal unitPrice, int quantity) {
    }

    public record Summary(BigDecimal subtotal, String discountCode, BigDecimal discountAmount, BigDecimal total) {
    }

    private final Map<DiscountType, DiscountStrategy> strategies = new EnumMap<>(DiscountType.class);

    public PricingService(List<DiscountStrategy> discountStrategies) {
        discountStrategies.forEach(s -> strategies.put(s.type(), s));
    }

    public BigDecimal subtotal(List<Line> lines) {
        return lines.stream()
                .map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // R-CART-05: active, inside the validity window and subtotal at or above the minimum.
    public boolean applicable(DiscountRule rule, BigDecimal subtotal, Instant now) {
        return rule.isActive()
                && (rule.getValidFrom() == null || !now.isBefore(rule.getValidFrom()))
                && (rule.getValidTo() == null || !now.isAfter(rule.getValidTo()))
                && subtotal.compareTo(rule.getMinimumOrderTotal()) >= 0;
    }

    public Summary summarise(List<Line> lines, DiscountRule rule, Instant now) {
        BigDecimal subtotal = subtotal(lines);
        if (rule == null || !applicable(rule, subtotal, now)) {
            return new Summary(subtotal, null, BigDecimal.ZERO.setScale(2), subtotal);
        }
        DiscountStrategy strategy = strategies.get(rule.getType());
        BigDecimal discount = strategy.calculate(subtotal, rule).min(subtotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Summary(subtotal, rule.getCode(), discount, total);
    }
}
