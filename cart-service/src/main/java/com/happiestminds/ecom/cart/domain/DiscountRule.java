package com.happiestminds.ecom.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "discount_rules", uniqueConstraints = @UniqueConstraint(name = "uk_discount_code", columnNames = "code"))
public class DiscountRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DiscountType type;

    // Named discountValue because VALUE is a reserved word in H2.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumOrderTotal;

    @Column(nullable = false)
    private boolean active;

    private Instant validFrom;

    private Instant validTo;

    protected DiscountRule() {
    }

    public DiscountRule(String code, DiscountType type, BigDecimal discountValue, BigDecimal minimumOrderTotal,
                        boolean active, Instant validFrom, Instant validTo) {
        this.code = code;
        this.type = type;
        this.discountValue = discountValue;
        this.minimumOrderTotal = minimumOrderTotal;
        this.active = active;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public DiscountType getType() {
        return type;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getMinimumOrderTotal() {
        return minimumOrderTotal;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }
}
