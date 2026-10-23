package com.happiestminds.ecom.cart.config;

import com.happiestminds.ecom.cart.domain.DiscountRule;
import com.happiestminds.ecom.cart.domain.DiscountType;
import com.happiestminds.ecom.cart.repository.DiscountRuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final DiscountRuleRepository discounts;

    public DataSeeder(DiscountRuleRepository discounts) {
        this.discounts = discounts;
    }

    @Override
    public void run(String... args) {
        seed("WELCOME10", DiscountType.PERCENT, "10.00", "500.00");
        seed("FLAT200", DiscountType.FIXED, "200.00", "2000.00");
    }

    private void seed(String code, DiscountType type, String value, String minimumOrderTotal) {
        if (!discounts.existsByCode(code)) {
            discounts.save(new DiscountRule(code, type, new BigDecimal(value), new BigDecimal(minimumOrderTotal),
                    true, null, null));
        }
    }
}
