package com.happiestminds.ecom.order.service;

import com.happiestminds.ecom.order.client.CartClient;
import com.happiestminds.ecom.order.domain.Order;
import com.happiestminds.ecom.order.domain.OrderItem;
import com.happiestminds.ecom.order.domain.OrderStatus;
import com.happiestminds.ecom.order.repository.OrderRepository;
import com.happiestminds.ecom.order.service.exception.InvalidStateException;
import com.happiestminds.ecom.order.service.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// One short local transaction per persisted state change; the orchestrator never calls a remote service inside one.
@Component
public class OrderTransactions {

    private static final Logger log = LoggerFactory.getLogger(OrderTransactions.class);

    private final OrderRepository orders;

    public OrderTransactions(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public String createOrder(String customerId, String idempotencyKey, String requestHash,
                              CartClient.CheckoutSummary summary, String simulatedPaymentOutcome) {
        Order order = new Order(customerId, idempotencyKey, requestHash, summary.cartId(), summary.subtotal(),
                summary.discountAmount(), summary.total(), summary.currency(), simulatedPaymentOutcome,
                MDC.get("correlationId"));
        for (CartClient.Item i : summary.items()) {
            order.addItem(new OrderItem(order, i.productId(), i.sku(), i.productName(), i.quantity(),
                    i.unitPrice(), i.lineTotal()));
        }
        order.recordCreation();
        String id = orders.saveAndFlush(order).getId();
        log.info("Order {} created for customer {} total={}", id, customerId, summary.total());
        return id;
    }

    @Transactional
    public void markReserved(String orderId, String reservationRef) {
        Order order = load(orderId);
        order.setReservationRef(reservationRef);
        move(order, OrderStatus.STOCK_RESERVED, "stock reserved");
    }

    @Transactional
    public void transition(String orderId, OrderStatus target, String reason) {
        move(load(orderId), target, reason);
    }

    // Records the business failure reason on the order and then moves it.
    @Transactional
    public void fail(String orderId, OrderStatus target, String failureReason) {
        Order order = load(orderId);
        order.setFailureReason(failureReason);
        move(order, target, failureReason);
    }

    @Transactional
    public void confirm(String orderId, String paymentRef) {
        Order order = load(orderId);
        order.setPaymentRef(paymentRef);
        move(order, OrderStatus.CONFIRMED, "payment succeeded and stock confirmed");
    }

    @Transactional
    public void setPaymentRef(String orderId, String paymentRef) {
        load(orderId).setPaymentRef(paymentRef);
    }

    @Transactional
    public void markCartCheckedOut(String orderId) {
        load(orderId).setCartCheckedOut(true);
    }

    private Order load(String orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }

    private static void move(Order order, OrderStatus target, String reason) {
        if (!order.getStatus().canTransitionTo(target)) {
            throw new InvalidStateException("INVALID_ORDER_STATE",
                    "Order cannot move from " + order.getStatus() + " to " + target);
        }
        OrderStatus from = order.getStatus();
        order.moveTo(target, reason);
        log.info("Order {} {} -> {} ({})", order.getId(), from, target, reason);
    }
}
