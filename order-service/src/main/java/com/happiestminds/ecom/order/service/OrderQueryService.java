package com.happiestminds.ecom.order.service;

import com.happiestminds.ecom.order.api.dto.OrderResponse;
import com.happiestminds.ecom.order.api.dto.PageResponse;
import com.happiestminds.ecom.order.domain.Order;
import com.happiestminds.ecom.order.repository.OrderRepository;
import com.happiestminds.ecom.order.service.exception.ForbiddenException;
import com.happiestminds.ecom.order.service.exception.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public OrderResponse view(String orderId) {
        return OrderResponse.from(find(orderId));
    }

    @Transactional(readOnly = true)
    public OrderResponse viewFor(String orderId, String callerId, boolean privileged) {
        Order order = find(orderId);
        requireOwnerOrPrivileged(order.getCustomerId(), callerId, privileged);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listFor(String customerId, String callerId, boolean privileged,
                                               Integer page, Integer size) {
        requireOwnerOrPrivileged(customerId, callerId, privileged);
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(orders.findByCustomerId(customerId, pageable).map(OrderResponse::from));
    }

    public static void requireOwnerOrPrivileged(String ownerId, String callerId, boolean privileged) {
        if (!privileged && !ownerId.equals(callerId)) {
            throw new ForbiddenException("You can only access your own orders");
        }
    }

    private Order find(String orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }
}
