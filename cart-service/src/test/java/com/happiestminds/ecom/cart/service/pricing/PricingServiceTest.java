package com.happiestminds.ecom.cart.service.pricing;

import com.happiestminds.ecom.cart.domain.DiscountRule;
import com.happiestminds.ecom.cart.domain.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    final PricingService pricing = new PricingService(List.of(new PercentDiscountStrategy(), new FixedDiscountStrategy()));
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private static PricingService.Line line(String price, int qty) {
        return new PricingService.Line(new BigDecimal(price), qty);
    }

    private static DiscountRule rule(DiscountType type, String value, String minimum, boolean active) {
        return new DiscountRule("CODE", type, new BigDecimal(value), new BigDecimal(minimum), active, null, null);
    }

    @Test
    @DisplayName("R-CART-04 PERCENT rounds HALF_UP to 2 decimals (10% of 999.99 = 100.00)")
    void shouldRoundPercentDiscountHalfUp() {
        PricingService.Summary s = pricing.summarise(List.of(line("999.99", 1)),
                rule(DiscountType.PERCENT, "10.00", "500.00", true), now);

        assertThat(s.discountAmount()).isEqualByComparingTo("100.00");
        assertThat(s.total()).isEqualByComparingTo("899.99");
        assertThat(s.discountCode()).isEqualTo("CODE");
    }

    @Test
    @DisplayName("R-CART-04 FIXED is capped at the subtotal and the total never goes below zero")
    void shouldCapFixedDiscountAtSubtotal() {
        PricingService.Summary s = pricing.summarise(List.of(line("150.00", 1)),
                rule(DiscountType.FIXED, "200.00", "0.00", true), now);

        assertThat(s.discountAmount()).isEqualByComparingTo("150.00");
        assertThat(s.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("R-CART-05 no discount when the minimum order total is not met")
    void shouldIgnoreDiscount_whenBelowMinimum() {
        PricingService.Summary s = pricing.summarise(List.of(line("400.00", 1)),
                rule(DiscountType.PERCENT, "10.00", "500.00", true), now);

        assertThat(s.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(s.discountCode()).isNull();
        assertThat(s.total()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("R-CART-05 inactive and expired rules do not apply")
    void shouldIgnoreInactiveAndExpiredRules() {
        DiscountRule inactive = rule(DiscountType.PERCENT, "10.00", "0.00", false);
        DiscountRule expired = new DiscountRule("OLD", DiscountType.PERCENT, new BigDecimal("10.00"),
                BigDecimal.ZERO, true, null, now.minusSeconds(1));

        assertThat(pricing.applicable(inactive, new BigDecimal("1000.00"), now)).isFalse();
        assertThat(pricing.applicable(expired, new BigDecimal("1000.00"), now)).isFalse();
    }

    @Test
    @DisplayName("AC-CART-03 R-CART-06 subtotal sums line totals with scale 2")
    void shouldSumLinesWithScaleTwo() {
        BigDecimal subtotal = pricing.subtotal(List.of(line("899.00", 2), line("1499.00", 1)));

        assertThat(subtotal).isEqualByComparingTo("3297.00");
        assertThat(subtotal.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("an empty cart without a rule totals 0.00")
    void shouldTotalZero_whenCartEmpty() {
        PricingService.Summary s = pricing.summarise(List.of(), null, now);

        assertThat(s.total()).isEqualByComparingTo("0.00");
    }
}
