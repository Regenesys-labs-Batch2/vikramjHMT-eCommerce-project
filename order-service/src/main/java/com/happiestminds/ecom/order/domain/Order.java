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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Entity name and table avoid the reserved word ORDER in HQL and SQL.
@Entity(name = "CustomerOrder")
@Table(name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_customer_key", columnNames = {"customerId", "idempotencyKey"}),
        indexes = {
                @Index(name = "idx_order_customer", columnList = "customerId"),
                @Index(name = "idx_order_status", columnList = "status")
        })
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 64)
    private String reservationRef;

    @Column(length = 64)
    private String paymentRef;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(length = 64)
    private String cartRef;

    @Column(nullable = false)
    private boolean cartCheckedOut;

    @Column(length = 100)
    private String failureReason;

    @Column(length = 20)
    private String simulatedPaymentOutcome;

    @Column(length = 64)
    private String correlationId;

    @OneToMany(mappedBy = "order", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = jakarta.persistence.CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sequenceNo ASC")
    @BatchSize(size = 50)
    private List<OrderStatusHistory> history = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Order() {
    }

    public Order(String customerId, String idempotencyKey, String requestHash, String cartRef, BigDecimal subtotal,
                 BigDecimal discountAmount, BigDecimal totalAmount, String currency, String simulatedPaymentOutcome,
                 String correlationId) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.cartRef = cartRef;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.simulatedPaymentOutcome = simulatedPaymentOutcome;
        this.correlationId = correlationId;
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

    public void addItem(OrderItem item) {
        items.add(item);
    }

    // Callers must check OrderStatus.canTransitionTo first; this records the change and its audit row.
    public void moveTo(OrderStatus target, String reason) {
        history.add(new OrderStatusHistory(this, history.size() + 1, status, target, reason, correlationId));
        this.status = target;
    }

    public void recordCreation() {
        history.add(new OrderStatusHistory(this, history.size() + 1, null, OrderStatus.CREATED, "order created",
                correlationId));
    }

    public void setReservationRef(String reservationRef) {
        this.reservationRef = reservationRef;
    }

    public void setPaymentRef(String paymentRef) {
        this.paymentRef = paymentRef;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setCartCheckedOut(boolean cartCheckedOut) {
        this.cartCheckedOut = cartCheckedOut;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReservationRef() {
        return reservationRef;
    }

    public String getPaymentRef() {
        return paymentRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getCartRef() {
        return cartRef;
    }

    public boolean isCartCheckedOut() {
        return cartCheckedOut;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getSimulatedPaymentOutcome() {
        return simulatedPaymentOutcome;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public List<OrderStatusHistory> getHistory() {
        return history;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
