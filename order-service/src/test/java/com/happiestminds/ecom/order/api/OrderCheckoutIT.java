package com.happiestminds.ecom.order.api;

import com.happiestminds.ecom.order.client.CartClient;
import com.happiestminds.ecom.order.client.InventoryClient;
import com.happiestminds.ecom.order.client.PaymentClient;
import com.happiestminds.ecom.order.security.JwtService;
import com.happiestminds.ecom.order.service.exception.ConflictException;
import com.happiestminds.ecom.order.service.exception.DependencyUnavailableException;
import com.happiestminds.ecom.order.service.exception.InvalidStateException;
import com.happiestminds.ecom.order.service.exception.UnprocessableException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"ecom.order.reconcile-attempts=1", "ecom.order.reconcile-interval-ms=10"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderCheckoutIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @MockitoBean
    CartClient cart;
    @MockitoBean
    InventoryClient inventory;
    @MockitoBean
    PaymentClient payments;

    String customerId;
    String auth;

    @BeforeEach
    void setUp() {
        customerId = "cust-" + UUID.randomUUID();
        auth = bearer(customerId, "CUSTOMER");
        BigDecimal price = new BigDecimal("55000.00");
        when(cart.checkoutSummary()).thenReturn(new CartClient.CheckoutSummary("cart-1", customerId,
                List.of(new CartClient.Item("p1", "LAP-001", "ProBook 14", 1, price, price)),
                price, null, new BigDecimal("0.00"), price, "INR"));
        when(inventory.reserve(anyString(), any())).thenReturn(new InventoryClient.Reservation("res-1", "PENDING"));
        when(inventory.confirm("res-1")).thenReturn(new InventoryClient.Reservation("res-1", "CONFIRMED"));
        when(inventory.release("res-1")).thenReturn(new InventoryClient.Reservation("res-1", "RELEASED"));
        when(payments.create(anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(new PaymentClient.Payment("pay-1", "SUCCEEDED", null));
        when(payments.refund(eq("pay-1"), anyString())).thenReturn(new PaymentClient.Refund("ref-1", "COMPLETED"));
    }

    private String bearer(String subject, String role) {
        return "Bearer " + jwtService.issue(subject, subject + "@ecom.test", List.of(role));
    }

    private ResultActions checkout(String key, String body) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", auth).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : body));
    }

    private String checkoutOk(String key, String body) throws Exception {
        String json = checkout(key, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String onlyOrderId() throws Exception {
        String json = mvc.perform(get("/api/orders/customer/" + customerId).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.content[0].id");
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("AC-ORD-01 AC-ORD-02 successful checkout confirms the order with a full status history")
    void shouldConfirmOrder_whenPaymentSucceeds() throws Exception {
        checkout(newKey(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentRef").value("pay-1"))
                .andExpect(jsonPath("$.reservationRef").value("res-1"))
                .andExpect(jsonPath("$.totalAmount").value(55000.00))
                .andExpect(jsonPath("$.items[0].unitPrice").value(55000.00))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("CREATED"))
                .andExpect(jsonPath("$.statusHistory[1].toStatus").value("STOCK_RESERVED"))
                .andExpect(jsonPath("$.statusHistory[2].toStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.statusHistory[3].toStatus").value("CONFIRMED"));
        verify(inventory).confirm("res-1");
        verify(cart).markCheckedOut("cart-1");
    }

    @Test
    @DisplayName("AC-ORD-06 R-ORD-01 a repeated Idempotency-Key returns the same order without a second reservation or payment")
    void shouldReturnExistingOrder_whenKeyRepeated() throws Exception {
        String key = newKey();
        String id = checkoutOk(key, null);

        checkout(key, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        verify(inventory, times(1)).reserve(anyString(), any());
        verify(payments, times(1)).create(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("R-ORD-01 the same key with a different body is rejected with 422 IDEMPOTENCY_CONFLICT")
    void shouldReturn422_whenKeyReusedWithDifferentBody() throws Exception {
        String key = newKey();
        checkoutOk(key, null);

        checkout(key, "{\"simulatedPaymentOutcome\":\"DECLINED\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("AC-ORD-03 R-ORD-04 a declined payment releases the reservation and cancels the order")
    void shouldCancelAndReleaseStock_whenPaymentDeclined() throws Exception {
        when(payments.create(anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(new PaymentClient.Payment("pay-2", "DECLINED", "CARD_DECLINED"));

        checkout(newKey(), "{\"simulatedPaymentOutcome\":\"DECLINED\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.failureReason").value("CARD_DECLINED"));
        verify(inventory).release("res-1");
        verify(inventory, never()).confirm(anyString());
        verify(cart, never()).markCheckedOut(anyString());
    }

    @Test
    @DisplayName("R-ORD-04 a failed release leaves COMPENSATION_PENDING and recover completes it")
    void shouldStayCompensationPending_whenReleaseFails_andRecoverCancels() throws Exception {
        when(payments.create(anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(new PaymentClient.Payment("pay-2", "DECLINED", "CARD_DECLINED"));
        when(inventory.release("res-1")).thenThrow(new DependencyUnavailableException("inventory down"));

        checkout(newKey(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPENSATION_PENDING"));
        String id = onlyOrderId();

        doReturn(new InventoryClient.Reservation("res-1", "RELEASED")).when(inventory).release("res-1");
        mvc.perform(post("/api/orders/" + id + "/recover").header("Authorization", bearer("admin-1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("AC-ORD-04 insufficient stock returns 409, the order is STOCK_UNAVAILABLE and no payment is made")
    void shouldReturn409_whenStockInsufficient() throws Exception {
        when(inventory.reserve(anyString(), any()))
                .thenThrow(new ConflictException("INSUFFICIENT_STOCK", "Requested quantity exceeds available stock"));

        checkout(newKey(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        mvc.perform(get("/api/orders/" + onlyOrderId()).header("Authorization", auth))
                .andExpect(jsonPath("$.status").value("STOCK_UNAVAILABLE"));
        verify(payments, never()).create(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("R-ORD-07 an unreachable Inventory returns 503, cancels the order and creates no payment")
    void shouldReturn503_whenInventoryUnavailable() throws Exception {
        when(inventory.reserve(anyString(), any())).thenThrow(new DependencyUnavailableException("inventory down"));

        checkout(newKey(), null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        mvc.perform(get("/api/orders/" + onlyOrderId()).header("Authorization", auth))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.failureReason").value("INVENTORY_UNAVAILABLE"));
        verify(payments, never()).create(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("R-ORD-05 a payment timeout leaves PAYMENT_PENDING; recover reconciles with the same key and confirms")
    void shouldReconcileTimedOutPayment() throws Exception {
        when(payments.create(anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new DependencyUnavailableException("payment timed out"));

        checkout(newKey(), "{\"simulatedPaymentOutcome\":\"TIMEOUT\"}")
                .andExpect(status().isServiceUnavailable());
        String id = onlyOrderId();
        mvc.perform(get("/api/orders/" + id).header("Authorization", auth))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));

        doReturn(new PaymentClient.Payment("pay-1", "SUCCEEDED", null))
                .when(payments).create(anyString(), anyString(), any(), anyString(), any());
        mvc.perform(post("/api/orders/" + id + "/recover").header("Authorization", bearer("order-service", "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentRef").value("pay-1"));
        // The reconciliation call reuses the order id as Idempotency-Key, so only one logical payment exists.
        verify(payments, times(2)).create(eq(id), eq(customerId), any(), eq("INR"), eq("TIMEOUT"));
    }

    @Test
    @DisplayName("R-ORD-08 paid but the reservation expired: refund and cancel with RESERVATION_LOST, never CONFIRMED")
    void shouldRefundAndCancel_whenReservationLost() throws Exception {
        when(inventory.confirm("res-1"))
                .thenThrow(new InvalidStateException("INVALID_RESERVATION_STATE", "Reservation is EXPIRED"));

        checkout(newKey(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.failureReason").value("RESERVATION_LOST"));
        verify(payments).refund("pay-1", "RESERVATION_LOST");
    }

    @Test
    @DisplayName("R-ORD-09 a failing cart checked-out call never fails a CONFIRMED order; recover re-sends it")
    void shouldKeepOrderConfirmed_whenCartCallFails() throws Exception {
        doThrow(new DependencyUnavailableException("cart down")).when(cart).markCheckedOut(anyString());

        checkout(newKey(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        String id = onlyOrderId();
        org.mockito.Mockito.reset(cart);
        mvc.perform(post("/api/orders/" + id + "/recover").header("Authorization", bearer("admin-1", "ADMIN")))
                .andExpect(status().isOk());
        verify(cart).markCheckedOut("cart-1");
    }

    @Test
    @DisplayName("AC-ORD-05 R-ORD-06 cancelling a CONFIRMED order refunds, restocks idempotently and cannot be repeated")
    void shouldCancelConfirmedOrder() throws Exception {
        String id = checkoutOk(newKey(), null);

        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(payments).refund("pay-1", "ORDER_CANCELLED");
        verify(inventory).restock("p1", 1, "cancel-" + id);
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
    }

    @Test
    @DisplayName("R-ORD-06 an empty cart returns 422 EMPTY_CART and creates no order")
    void shouldReturn422_whenCartEmpty() throws Exception {
        when(cart.checkoutSummary()).thenThrow(new UnprocessableException("EMPTY_CART", "Cart is empty"));

        checkout(newKey(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
        mvc.perform(get("/api/orders/customer/" + customerId).header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(inventory, never()).reserve(anyString(), any());
    }

    @Test
    @DisplayName("security: ownership, roles and the required Idempotency-Key header")
    void shouldEnforceOwnershipRolesAndHeaders() throws Exception {
        String id = checkoutOk(newKey(), null);
        String stranger = bearer("cust-other", "CUSTOMER");

        mvc.perform(get("/api/orders/" + id).header("Authorization", stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/customer/" + customerId).header("Authorization", stranger))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", stranger))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/" + id + "/recover").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/" + id).header("Authorization", bearer("admin-1", "ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/orders/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/" + UUID.randomUUID()).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mvc.perform(post("/api/orders").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
        checkout("not-a-uuid", null).andExpect(status().isBadRequest());
        checkout(newKey(), "{\"simulatedPaymentOutcome\":\"BOGUS\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
