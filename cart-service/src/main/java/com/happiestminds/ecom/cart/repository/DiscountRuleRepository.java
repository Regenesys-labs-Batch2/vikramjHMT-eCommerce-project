package com.happiestminds.ecom.cart.repository;

import com.happiestminds.ecom.cart.domain.DiscountRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiscountRuleRepository extends JpaRepository<DiscountRule, String> {
    Optional<DiscountRule> findByCode(String code);

    boolean existsByCode(String code);
}
