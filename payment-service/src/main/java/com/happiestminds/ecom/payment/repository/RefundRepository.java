package com.happiestminds.ecom.payment.repository;

import com.happiestminds.ecom.payment.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, String> {
    Optional<Refund> findByPaymentId(String paymentId);
}
