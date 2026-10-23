package com.happiestminds.ecom.cart.api;

import com.happiestminds.ecom.cart.client.CatalogClient;
import com.happiestminds.ecom.cart.security.JwtService;
import com.happiestminds.ecom.cart.service.exception.NotFoundException;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartControllerIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @MockitoBean
    CatalogClient catalog;

    String customerId;
    String auth;

    @BeforeEach
    void setUp() {
        customerId = "cust-" + UUID.randomUUID();
        auth = "Bearer " + jwtService.issue(customerId, "c@ecom.test", List.of("CUSTOMER"));
        when(catalog.getProduct("laptop")).thenReturn(product("laptop", "LAP-001", "ProBook 14", "55000.00", true));
        when(catalog.getProduct("mouse")).thenReturn(product("mouse", "ACC-002", "Wireless Mouse", "899.00", true));
        when(catalog.getProduct("retired")).thenReturn(product("retired", "OLD-001", "Retired", "10.00", false));
        when(catalog.getProduct("ghost")).thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
    }

    private static CatalogClient.ProductInfo product(String id, String sku, String name, String price, boolean active) {
        return new CatalogClient.ProductInfo(id, sku, name, active,
                new CatalogClient.Price(new BigDecimal(price), "INR"));
    }

    private String addItem(String productId, int quantity) throws Exception {
        String body = mvc.perform(post("/api/carts/items").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[0].id");
    }

    @Test
    @DisplayName("R-CART-03 adding an existing product increases its quantity and totals use BigDecimal scale 2")
    void shouldAddAndMergeItems() throws Exception {
        addItem("mouse", 2);

        mvc.perform(post("/api/carts/items").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"mouse\",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.subtotal").value(4495.00))
                .andExpect(jsonPath("$.total").value(4495.00))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    @DisplayName("AC-CART-01 R-CART-01 quantities outside 1..100 are rejected with INVALID_QUANTITY")
    void shouldRejectInvalidQuantities() throws Exception {
        for (int quantity : new int[]{0, -1, 101}) {
            mvc.perform(post("/api/carts/items").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"mouse\",\"quantity\":" + quantity + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        }
        addItem("mouse", 60);
        mvc.perform(post("/api/carts/items").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"mouse\",\"quantity\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    @DisplayName("R-CART-02 unknown and inactive products return 404 PRODUCT_NOT_FOUND")
    void shouldReturn404_whenProductUnknownOrInactive() throws Exception {
        for (String productId : new String[]{"ghost", "retired"}) {
            mvc.perform(post("/api/carts/items").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("update and delete cart items")
    void shouldUpdateAndRemoveItems() throws Exception {
        String itemId = addItem("mouse", 1);

        mvc.perform(put("/api/carts/items/" + itemId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(4))
                .andExpect(jsonPath("$.subtotal").value(3596.00));
        mvc.perform(put("/api/carts/items/" + itemId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/carts/items/" + itemId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/carts/current").header("Authorization", auth))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(delete("/api/carts/items/" + itemId).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-CART-02 R-CART-04 R-CART-05 seeded discounts follow the business rules")
    void shouldApplyDiscountRules() throws Exception {
        addItem("mouse", 1);

        mvc.perform(post("/api/carts/discount").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FLAT200\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DISCOUNT_NOT_APPLICABLE"));
        mvc.perform(post("/api/carts/discount").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOPE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISCOUNT_NOT_FOUND"));
        mvc.perform(post("/api/carts/discount").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"welcome10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCode").value("WELCOME10"))
                .andExpect(jsonPath("$.discountAmount").value(89.90))
                .andExpect(jsonPath("$.total").value(809.10));
    }

    @Test
    @DisplayName("AC-CART-04 R-CART-03 checkout summary re-reads Catalog prices and returns trusted totals")
    void shouldReturnCheckoutSummaryWithFreshPrices() throws Exception {
        addItem("laptop", 1);
        when(catalog.getProduct("laptop")).thenReturn(product("laptop", "LAP-001", "ProBook 14", "50000.00", true));

        mvc.perform(post("/api/carts/checkout-summary").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.items[0].unitPrice").value(50000.00))
                .andExpect(jsonPath("$.items[0].lineTotal").value(50000.00))
                .andExpect(jsonPath("$.total").value(50000.00));
    }

    @Test
    @DisplayName("R-CART-03 an empty cart cannot be summarised (422 EMPTY_CART)")
    void shouldReturn422_whenCartEmpty() throws Exception {
        mvc.perform(post("/api/carts/checkout-summary").header("Authorization", auth))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
    }

    @Test
    @DisplayName("checked-out carts are replaced by a new ACTIVE cart; only SERVICE can mark them")
    void shouldStartNewCartAfterCheckout() throws Exception {
        addItem("mouse", 1);
        String cartId = JsonPath.read(mvc.perform(get("/api/carts/current").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/carts/" + cartId + "/checked-out").header("Authorization", auth))
                .andExpect(status().isForbidden());
        String serviceAuth = "Bearer " + jwtService.issueServiceToken("order-service");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/carts/" + cartId + "/checked-out").header("Authorization", serviceAuth))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/carts/current").header("Authorization", auth))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("security: anonymous 401, non-admin discount management 403, admin can create rules")
    void shouldEnforceSecurity() throws Exception {
        mvc.perform(get("/api/carts/current")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/discounts").header("Authorization", auth)).andExpect(status().isForbidden());

        String admin = "Bearer " + jwtService.issue("admin-1", "a@ecom.test", List.of("ADMIN"));
        mvc.perform(post("/api/discounts").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"t-" + UUID.randomUUID().toString().substring(0, 6)
                                + "\",\"type\":\"FIXED\",\"value\":50,\"minimumOrderTotal\":100}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/discounts").header("Authorization", admin)).andExpect(status().isOk());
    }
}
