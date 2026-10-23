package com.happiestminds.ecom.catalog.repository;

import com.happiestminds.ecom.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, String> {
    boolean existsByName(String name);

    java.util.Optional<Category> findByName(String name);
}
