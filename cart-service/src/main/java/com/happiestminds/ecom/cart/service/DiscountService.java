package com.happiestminds.ecom.cart.service;

import com.happiestminds.ecom.cart.api.dto.DiscountRequest;
import com.happiestminds.ecom.cart.api.dto.DiscountResponse;
import com.happiestminds.ecom.cart.domain.DiscountRule;
import com.happiestminds.ecom.cart.domain.DiscountType;
import com.happiestminds.ecom.cart.repository.DiscountRuleRepository;
import com.happiestminds.ecom.cart.service.exception.BadRequestException;
import com.happiestminds.ecom.cart.service.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class DiscountService {

    private final DiscountRuleRepository discounts;

    public DiscountService(DiscountRuleRepository discounts) {
        this.discounts = discounts;
    }

    @Transactional
    public DiscountResponse create(DiscountRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (request.type() == DiscountType.PERCENT && request.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("VALIDATION_ERROR", "A PERCENT discount cannot exceed 100");
        }
        if (discounts.existsByCode(code)) {
            throw exists();
        }
        DiscountRule rule = new DiscountRule(code, request.type(), request.value().setScale(2, RoundingMode.HALF_UP),
                request.minimumOrderTotal().setScale(2, RoundingMode.HALF_UP),
                request.active() == null || request.active(), request.validFrom(), request.validTo());
        try {
            return DiscountResponse.from(discounts.saveAndFlush(rule));
        } catch (DataIntegrityViolationException e) {
            throw exists();
        }
    }

    @Transactional(readOnly = true)
    public List<DiscountResponse> list() {
        return discounts.findAll().stream().map(DiscountResponse::from).toList();
    }

    private static ConflictException exists() {
        return new ConflictException("DISCOUNT_ALREADY_EXISTS", "A discount with this code already exists");
    }
}
