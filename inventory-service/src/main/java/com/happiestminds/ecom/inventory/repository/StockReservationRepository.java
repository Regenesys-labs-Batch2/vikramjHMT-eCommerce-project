package com.happiestminds.ecom.inventory.repository;

import com.happiestminds.ecom.inventory.domain.ReservationStatus;
import com.happiestminds.ecom.inventory.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, String> {

    List<StockReservation> findByOrderRef(String orderRef);

    @Query("select r.id from StockReservation r where r.status = :status and r.expiresAt < :now")
    List<String> findIdsByStatusAndExpiresAtBefore(@Param("status") ReservationStatus status, @Param("now") Instant now);
}
