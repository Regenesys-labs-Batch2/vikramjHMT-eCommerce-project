package com.happiestminds.ecom.catalog.repository;

import com.happiestminds.ecom.catalog.domain.Product;
import com.happiestminds.ecom.catalog.domain.ProductPrice;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> search(String q, String categoryId, Boolean active,
                                                BigDecimal minPrice, BigDecimal maxPrice, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
            }
            if (categoryId != null && !categoryId.isBlank()) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (minPrice != null || maxPrice != null) {
                Subquery<String> sub = query.subquery(String.class);
                Root<ProductPrice> price = sub.from(ProductPrice.class);
                List<Predicate> inner = new ArrayList<>();
                inner.add(cb.equal(price.get("product"), root));
                inner.add(cb.lessThanOrEqualTo(price.<Instant>get("validFrom"), now));
                inner.add(cb.or(cb.isNull(price.get("validTo")), cb.greaterThan(price.<Instant>get("validTo"), now)));
                if (minPrice != null) {
                    inner.add(cb.greaterThanOrEqualTo(price.<BigDecimal>get("amount"), minPrice));
                }
                if (maxPrice != null) {
                    inner.add(cb.lessThanOrEqualTo(price.<BigDecimal>get("amount"), maxPrice));
                }
                sub.select(price.<String>get("id")).where(inner.toArray(new Predicate[0]));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
