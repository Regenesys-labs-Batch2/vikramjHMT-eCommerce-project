package com.happiestminds.ecom.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "payment_attempts", indexes = @Index(name = "idx_attempt_payment", columnList = "payment_id"))
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentTransaction payment;

    @Column(nullable = false)
    private int attemptNumber;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(nullable = false)
    private long simulatedDelayMs;

    @Column(nullable = false)
    private Instant attemptedAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(PaymentTransaction payment, int attemptNumber, String outcome, long simulatedDelayMs) {
        this.payment = payment;
        this.attemptNumber = attemptNumber;
        this.outcome = outcome;
        this.simulatedDelayMs = simulatedDelayMs;
    }

    @PrePersist
    void onCreate() {
        attemptedAt = Instant.now();
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getOutcome() {
        return outcome;
    }

    public long getSimulatedDelayMs() {
        return simulatedDelayMs;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
