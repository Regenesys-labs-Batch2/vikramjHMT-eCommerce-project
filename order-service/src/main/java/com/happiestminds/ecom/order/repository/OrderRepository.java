package com.happiestminds.ecom.order.repository;

import com.happiestminds.ecom.order.domain.Order;
import com.happiestminds.ecom.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    List<Order> findByStatusInAndUpdatedAtBefore(Collection<OrderStatus> statuses, Instant before);

    List<Order> findByStatusAndCartCheckedOutFalseAndUpdatedAtBefore(OrderStatus status, Instant before);
}
