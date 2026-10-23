package com.happiestminds.ecom.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "order_status_history", indexes = @Index(name = "idx_history_order", columnList = "order_id"))
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus toStatus;

    @Column(length = 200)
    private String reason;

    @Column(length = 64)
    private String correlationId;

    @Column(nullable = false)
    private Instant changedAt;

    protected OrderStatusHistory() {
    }

    public OrderStatusHistory(Order order, int sequenceNo, OrderStatus fromStatus, OrderStatus toStatus,
                              String reason, String correlationId) {
        this.order = order;
        this.sequenceNo = sequenceNo;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    @PrePersist
    void onCreate() {
        changedAt = Instant.now();
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
