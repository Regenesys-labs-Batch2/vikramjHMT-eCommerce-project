package com.happiestminds.ecom.inventory.repository;

import com.happiestminds.ecom.inventory.domain.InventoryStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

// Each stock change is one conditional UPDATE whose row count decides success (no read-then-write race).
// No clearAutomatically: callers keep modifying already loaded reservation entities in the same transaction.
public interface InventoryStockRepository extends JpaRepository<InventoryStock, String> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update InventoryStock s
               set s.availableQuantity = s.availableQuantity - :qty,
                   s.reservedQuantity = s.reservedQuantity + :qty,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             where s.productId = :productId and s.availableQuantity >= :qty
            """)
    int reserve(@Param("productId") String productId, @Param("qty") int qty, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update InventoryStock s
               set s.reservedQuantity = s.reservedQuantity - :qty,
                   s.availableQuantity = s.availableQuantity + :qty,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             where s.productId = :productId and s.reservedQuantity >= :qty
            """)
    int release(@Param("productId") String productId, @Param("qty") int qty, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update InventoryStock s
               set s.reservedQuantity = s.reservedQuantity - :qty,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             where s.productId = :productId and s.reservedQuantity >= :qty
            """)
    int confirm(@Param("productId") String productId, @Param("qty") int qty, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update InventoryStock s
               set s.availableQuantity = s.availableQuantity + :delta,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             where s.productId = :productId and s.availableQuantity + :delta >= 0
            """)
    int adjust(@Param("productId") String productId, @Param("delta") int delta, @Param("now") Instant now);
}
