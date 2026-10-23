package com.happiestminds.ecom.catalog.repository;

import com.happiestminds.ecom.catalog.domain.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, String> {

    // Ordered by validFrom ascending so the latest window wins when callers put the rows in a map.
    @Query("""
            select pr from ProductPrice pr
            where pr.product.id in :ids and pr.validFrom <= :now and (pr.validTo is null or pr.validTo > :now)
            order by pr.validFrom asc
            """)
    List<ProductPrice> findCurrent(@Param("ids") Collection<String> ids, @Param("now") Instant now);
}
