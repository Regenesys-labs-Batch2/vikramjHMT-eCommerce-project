package com.happiestminds.ecom.inventory.repository;

import com.happiestminds.ecom.inventory.domain.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, String> {
    Optional<InventoryAdjustment> findByProductIdAndReference(String productId, String reference);
}
