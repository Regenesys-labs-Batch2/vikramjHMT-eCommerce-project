package com.happiestminds.ecom.payment.api;

import com.happiestminds.ecom.payment.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ecom.payment.simulated-timeout-ms=100")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;

    private String service() {
        return "Bearer " + jwtService.issueServiceToken("order-service");
    }

    private String customer(String id) {
        return "Bearer " + jwtService.issue(id, "c@ecom.test", List.of("CUSTOMER"));
    }

    private static String body(String orderId, String customerId, String amount, String outcome) {
        return "{\"orderId\":\"" + orderId + "\",\"customerId\":\"" + customerId + "\",\"amount\":" + amount
                + ",\"currency\":\"INR\"" + (outcome == null ? "" : ",\"simulatedOutcome\":\"" + outcome + "\"") + "}";
    }

    private String pay(String key, String orderId, String customerId, String amount, String outcome) throws Exception {
        String response = mvc.perform(post("/api/payments").header("Authorization", service())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(body(orderId, customerId, amount, outcome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    @DisplayName("AC-PAY-01 R-PAY-01 same key and same request returns the original payment; no second charge")
    void shouldReturnOriginalPayment_whenKeyRepeated() throws Exception {
        String key = UUID.randomUUID().toString();
        String orderId = "o-" + UUID.randomUUID();
        String id = pay(key, orderId, "c-1", "1000.00", null);

        mvc.perform(post("/api/payments").header("Authorization", service()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body(orderId, "c-1", "1000.00", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/payments").param("orderId", orderId).header("Authorization", service()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("AC-PAY-02 R-PAY-01 same key with a different request is rejected with 422")
    void shouldReturn422_whenKeyReusedWithDifferentRequest() throws Exception {
        String key = UUID.randomUUID().toString();
        String orderId = "o-" + UUID.randomUUID();
        pay(key, orderId, "c-1", "1000.00", null);

        mvc.perform(post("/api/payments").header("Authorization", service()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body(orderId, "c-1", "2000.00", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("AC-PAY-03 R-PAY-02 declined payments are recorded with an attempt and failureReason")
    void shouldRecordDeclinedPayment() throws Exception {
        String id = pay(UUID.randomUUID().toString(), "o-" + UUID.randomUUID(), "c-1", "72999.99", null);

        mvc.perform(get("/api/payments/" + id).header("Authorization", service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.failureReason").value("CARD_DECLINED"))
                .andExpect(jsonPath("$.attempts[0].outcome").value("DECLINED"));
    }

    @Test
    @DisplayName("R-PAY-02 a simulated TIMEOUT still ends as SUCCEEDED")
    void shouldSucceedAfterSimulatedTimeout() throws Exception {
        String id = pay(UUID.randomUUID().toString(), "o-" + UUID.randomUUID(), "c-1", "500.00", "TIMEOUT");

        mvc.perform(get("/api/payments/" + id).header("Authorization", service()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attempts[0].outcome").value("TIMEOUT"));
    }

    @Test
    @DisplayName("AC-PAY-04 R-PAY-03 refund only from SUCCEEDED; a repeated refund returns the existing refund")
    void shouldRefundOnce() throws Exception {
        String id = pay(UUID.randomUUID().toString(), "o-" + UUID.randomUUID(), "c-1", "500.00", "SUCCESS");

        mvc.perform(post("/api/payments/" + id + "/refund").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"RESERVATION_LOST\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(post("/api/payments/" + id + "/refund").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/payments/" + id).header("Authorization", service()))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("R-PAY-03 a declined payment cannot be refunded")
    void shouldRejectRefund_whenNotSucceeded() throws Exception {
        String id = pay(UUID.randomUUID().toString(), "o-" + UUID.randomUUID(), "c-1", "500.00", "DECLINED");

        mvc.perform(post("/api/payments/" + id + "/refund").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATE"));
    }

    @Test
    @DisplayName("R-PAY-05 customers cannot create or refund payments and read only their own")
    void shouldEnforceRolesAndOwnership() throws Exception {
        mvc.perform(post("/api/payments").header("Authorization", customer("c-1"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body("o-1", "c-1", "10.00", null)))
                .andExpect(status().isForbidden());
        String id = pay(UUID.randomUUID().toString(), "o-" + UUID.randomUUID(), "c-1", "500.00", null);

        mvc.perform(get("/api/payments/" + id).header("Authorization", customer("c-1"))).andExpect(status().isOk());
        mvc.perform(get("/api/payments/" + id).header("Authorization", customer("c-2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/payments/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R-PAY-01 missing Idempotency-Key and invalid body return 400")
    void shouldReturn400_whenKeyMissingOrBodyInvalid() throws Exception {
        mvc.perform(post("/api/payments").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content(body("o-1", "c-1", "10.00", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
        mvc.perform(post("/api/payments").header("Authorization", service())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body("o-1", "c-1", "-5", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
