package com.happiestminds.ecom.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "inventory_adjustments",
        uniqueConstraints = @UniqueConstraint(name = "uk_adjustment_reference", columnNames = {"productId", "reference"}),
        indexes = @Index(name = "idx_adjustment_product", columnList = "productId"))
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 64)
    private String productId;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(length = 100)
    private String reference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryAdjustment() {
    }

    public InventoryAdjustment(String productId, int delta, String reason, String actor, String reference) {
        this.productId = productId;
        this.delta = delta;
        this.reason = reason;
        this.actor = actor;
        this.reference = reference;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public int getDelta() {
        return delta;
    }

    public String getReason() {
        return reason;
    }

    public String getActor() {
        return actor;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
