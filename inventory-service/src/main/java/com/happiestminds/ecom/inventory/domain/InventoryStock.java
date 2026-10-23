package com.happiestminds.ecom.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "inventory_stock")
public class InventoryStock {

    @Id
    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private int reservedQuantity;

    @Version
    private Long version;

    @Column(nullable = false)
    private Instant updatedAt;

    protected InventoryStock() {
    }

    public static InventoryStock of(String productId, int available, int reserved) {
        InventoryStock stock = new InventoryStock();
        stock.productId = productId;
        stock.availableQuantity = available;
        stock.reservedQuantity = reserved;
        return stock;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
