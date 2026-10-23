package com.happiestminds.ecom.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.happiestminds.ecom.order.domain.OrderStatus.CANCELLED;
import static com.happiestminds.ecom.order.domain.OrderStatus.COMPENSATION_PENDING;
import static com.happiestminds.ecom.order.domain.OrderStatus.CONFIRMED;
import static com.happiestminds.ecom.order.domain.OrderStatus.CREATED;
import static com.happiestminds.ecom.order.domain.OrderStatus.PAYMENT_FAILED;
import static com.happiestminds.ecom.order.domain.OrderStatus.PAYMENT_PENDING;
import static com.happiestminds.ecom.order.domain.OrderStatus.STOCK_RESERVED;
import static com.happiestminds.ecom.order.domain.OrderStatus.STOCK_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    @DisplayName("AC-ORD-05 R-ORD-02 every legal transition from the state table is allowed")
    void shouldAllowTheDocumentedTransitions() {
        assertThat(CREATED.canTransitionTo(STOCK_RESERVED)).isTrue();
        assertThat(CREATED.canTransitionTo(STOCK_UNAVAILABLE)).isTrue();
        assertThat(CREATED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(STOCK_RESERVED.canTransitionTo(PAYMENT_PENDING)).isTrue();
        assertThat(PAYMENT_PENDING.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(PAYMENT_PENDING.canTransitionTo(PAYMENT_FAILED)).isTrue();
        assertThat(PAYMENT_PENDING.canTransitionTo(COMPENSATION_PENDING)).isTrue();
        assertThat(PAYMENT_FAILED.canTransitionTo(COMPENSATION_PENDING)).isTrue();
        assertThat(PAYMENT_FAILED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(COMPENSATION_PENDING.canTransitionTo(CANCELLED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("AC-ORD-01 R-ORD-03 an order can never be confirmed without passing PAYMENT_PENDING")
    void shouldNotConfirmWithoutPayment() {
        for (OrderStatus from : EnumSet.complementOf(EnumSet.of(PAYMENT_PENDING))) {
            assertThat(from.canTransitionTo(CONFIRMED)).as("%s -> CONFIRMED", from).isFalse();
        }
    }

    @Test
    @DisplayName("AC-ORD-05 terminal states have no outgoing transitions")
    void shouldHaveNoTransitionsFromTerminalStates() {
        Set<OrderStatus> terminal = EnumSet.of(STOCK_UNAVAILABLE, CANCELLED);

        for (OrderStatus from : terminal) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isFalse();
            }
        }
    }

    @Test
    @DisplayName("a state never transitions to itself")
    void shouldRejectSelfTransitions() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("%s -> %s", status, status).isFalse();
        }
    }
}
