package com.happiestminds.ecom.inventory.api;

import com.happiestminds.ecom.inventory.security.JwtService;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryControllerIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;

    private String service() {
        return "Bearer " + jwtService.issueServiceToken("order-service");
    }

    private String customer() {
        return "Bearer " + jwtService.issue("cust-1", "c@ecom.test", List.of("CUSTOMER"));
    }

    private String newProduct(int quantity) throws Exception {
        String productId = "P-" + UUID.randomUUID();
        mvc.perform(post("/api/inventory/" + productId + "/adjustments").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":" + quantity + ",\"reason\":\"restock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(quantity));
        return productId;
    }

    private static String reserveBody(String orderRef, String productId, int quantity) {
        return "{\"orderRef\":\"" + orderRef + "\",\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":"
                + quantity + "}]}";
    }

    private String reserve(String orderRef, String productId, int quantity) throws Exception {
        String body = mvc.perform(post("/api/inventory/reservations").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content(reserveBody(orderRef, productId, quantity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.reservationId");
    }

    private void assertStock(String productId, int available, int reserved) throws Exception {
        mvc.perform(get("/api/inventory/" + productId).header("Authorization", customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(available))
                .andExpect(jsonPath("$.reservedQuantity").value(reserved));
    }

    @Test
    @DisplayName("AC-INV-01 R-INV-01 reservation above available stock returns 409 INSUFFICIENT_STOCK")
    void shouldReturn409_whenReservationExceedsStock() throws Exception {
        String productId = newProduct(2);

        mvc.perform(post("/api/inventory/reservations").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content(reserveBody("o-" + UUID.randomUUID(), productId, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.details[0].issue").value(productId));
        assertStock(productId, 2, 0);
    }

    @Test
    @DisplayName("R-INV-03 a repeated orderRef returns the existing reservation without reserving twice")
    void shouldReturnExistingReservation_whenOrderRefRepeated() throws Exception {
        String productId = newProduct(5);
        String orderRef = "o-" + UUID.randomUUID();
        String id = reserve(orderRef, productId, 2);

        mvc.perform(post("/api/inventory/reservations").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content(reserveBody(orderRef, productId, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(id));
        assertStock(productId, 3, 2);
    }

    @Test
    @DisplayName("AC-INV-03 R-INV-04 confirm is idempotent and removes the units from reserved")
    void shouldConfirmIdempotently() throws Exception {
        String productId = newProduct(5);
        String id = reserve("o-" + UUID.randomUUID(), productId, 2);

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/inventory/reservations/" + id + "/confirm").header("Authorization", service()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }
        assertStock(productId, 3, 0);
        mvc.perform(post("/api/inventory/reservations/" + id + "/release").header("Authorization", service()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));
    }

    @Test
    @DisplayName("AC-INV-04 AC-INV-05 releasing twice returns stock once; confirming a released reservation is rejected")
    void shouldReleaseOnlyOnce() throws Exception {
        String productId = newProduct(5);
        String id = reserve("o-" + UUID.randomUUID(), productId, 2);
        assertStock(productId, 3, 2);

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/inventory/reservations/" + id + "/release").header("Authorization", service()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RELEASED"));
        }
        assertStock(productId, 5, 0);
        mvc.perform(post("/api/inventory/reservations/" + id + "/confirm").header("Authorization", service()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));
    }

    @Test
    @DisplayName("R-INV-07 R-INV-06 customers cannot reserve or adjust")
    void shouldRejectCustomersOnInternalEndpoints() throws Exception {
        mvc.perform(post("/api/inventory/reservations").header("Authorization", customer())
                        .contentType(MediaType.APPLICATION_JSON).content(reserveBody("o-1", "p-1", 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/inventory/p-1/adjustments").header("Authorization", customer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":5,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/p-1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R-INV-06 negative adjustments are rejected and a repeated reference is applied once")
    void shouldApplyAdjustmentReferenceOnce() throws Exception {
        String productId = newProduct(10);

        mvc.perform(post("/api/inventory/" + productId + "/adjustments").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-11,\"reason\":\"loss\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEGATIVE_STOCK"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/inventory/" + productId + "/adjustments").header("Authorization", service())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"delta\":3,\"reason\":\"restock\",\"reference\":\"cancel-1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableQuantity").value(13));
        }
    }

    @Test
    @DisplayName("unknown product stock returns 404 STOCK_NOT_FOUND and unknown reservation 404")
    void shouldReturn404_whenUnknown() throws Exception {
        mvc.perform(get("/api/inventory/unknown-" + UUID.randomUUID()).header("Authorization", customer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
        mvc.perform(get("/api/inventory/reservations/" + UUID.randomUUID()).header("Authorization", customer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("invalid reservation bodies return 400")
    void shouldReturn400_whenReservationBodyInvalid() throws Exception {
        mvc.perform(post("/api/inventory/reservations").header("Authorization", service())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderRef\":\"o\",\"items\":[{\"productId\":\"p\",\"quantity\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
