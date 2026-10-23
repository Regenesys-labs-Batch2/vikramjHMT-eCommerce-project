package com.happiestminds.ecom.payment.repository;

import com.happiestminds.ecom.payment.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(String paymentId);

    long countByPaymentId(String paymentId);
}
