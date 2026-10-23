package com.happiestminds.ecom.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "stock_reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_order_product", columnNames = {"orderRef", "productId"}),
        indexes = {
                @Index(name = "idx_reservation_product", columnList = "productId"),
                @Index(name = "idx_reservation_status", columnList = "status")
        })
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 64)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(nullable = false, length = 64)
    private String orderRef;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected StockReservation() {
    }

    public static StockReservation pending(String orderRef, String productId, int quantity, long ttlMinutes) {
        StockReservation r = new StockReservation();
        r.orderRef = orderRef;
        r.productId = productId;
        r.quantity = quantity;
        r.expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));
        return r;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markStatus(ReservationStatus newStatus) {
        this.status = newStatus;
    }

    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
