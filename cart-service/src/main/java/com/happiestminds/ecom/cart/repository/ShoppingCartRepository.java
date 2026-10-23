package com.happiestminds.ecom.cart.repository;

import com.happiestminds.ecom.cart.domain.CartStatus;
import com.happiestminds.ecom.cart.domain.ShoppingCart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShoppingCartRepository extends JpaRepository<ShoppingCart, String> {
    Optional<ShoppingCart> findFirstByCustomerIdAndStatus(String customerId, CartStatus status);
}
