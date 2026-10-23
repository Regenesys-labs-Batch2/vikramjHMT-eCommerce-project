package com.happiestminds.ecom.payment.domain;

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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_idempotency", columnNames = "idempotencyKey"),
        indexes = {
                @Index(name = "idx_payment_order", columnList = "orderId"),
                @Index(name = "idx_payment_customer", columnList = "customerId"),
                @Index(name = "idx_payment_status", columnList = "status")
        })
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(length = 100)
    private String failureReason;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PaymentTransaction() {
    }

    public static PaymentTransaction initiated(String idempotencyKey, String requestHash, String orderId,
                                               String customerId, BigDecimal amount, String currency) {
        PaymentTransaction t = new PaymentTransaction();
        t.idempotencyKey = idempotencyKey;
        t.requestHash = requestHash;
        t.orderId = orderId;
        t.customerId = customerId;
        t.amount = amount;
        t.currency = currency;
        return t;
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

    public void record(PaymentStatus newStatus, String reason) {
        this.status = newStatus;
        this.failureReason = reason;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
