package com.happiestminds.ecom.catalog.api;

import com.happiestminds.ecom.catalog.security.JwtService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;

    private String admin() {
        return "Bearer " + jwtService.issue("admin-1", "admin@ecom.test", List.of("ADMIN", "CUSTOMER"));
    }

    private String customer() {
        return "Bearer " + jwtService.issue("cust-1", "c@ecom.test", List.of("CUSTOMER"));
    }

    private String createCategory() throws Exception {
        String body = mvc.perform(post("/api/categories").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat-" + UUID.randomUUID() + "\",\"description\":\"d\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String productJson(String sku, String name, String categoryId, String amount) {
        return "{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\",\"description\":\"desc\",\"categoryId\":\""
                + categoryId + "\",\"price\":{\"amount\":" + amount + ",\"currency\":\"INR\"},\"active\":true}";
    }

    private String createProduct(String sku, String name, String categoryId, String amount) throws Exception {
        String body = mvc.perform(post("/api/products").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(productJson(sku, name, categoryId, amount)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    @DisplayName("AC-CAT-02 admin creates a product; it is publicly readable and duplicate SKUs are rejected")
    void shouldCreateReadAndRejectDuplicateSku() throws Exception {
        String categoryId = createCategory();
        String id = createProduct("tst-001", "Test Book", categoryId, "1200.50");

        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TST-001"))
                .andExpect(jsonPath("$.price.amount").value(1200.50))
                .andExpect(jsonPath("$.price.currency").value("INR"));
        mvc.perform(get("/api/products/by-sku/TST-001")).andExpect(status().isOk());
        mvc.perform(post("/api/products").header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("TST-001", "Other", categoryId, "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("AC-CAT-01 R-CAT-02 invalid prices return 400 INVALID_PRICE")
    void shouldReturn400_whenPriceIsInvalid() throws Exception {
        String categoryId = createCategory();

        mvc.perform(post("/api/products").header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("TST-002", "Bad", categoryId, "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRICE"));
        mvc.perform(post("/api/products").header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("TST-002", "Bad", categoryId, "10.123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRICE"));
    }

    @Test
    @DisplayName("AC-CAT-05 R-CAT-03 customers and anonymous callers cannot modify the catalog")
    void shouldRejectWrites_whenNotAdmin() throws Exception {
        String body = productJson("TST-003", "Nope", "x", "10.00");

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products").header("Authorization", customer()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/categories").header("Authorization", customer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("R-CAT-01 a missing product returns 404 PRODUCT_NOT_FOUND")
    void shouldReturn404_whenProductMissing() throws Exception {
        mvc.perform(get("/api/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-CAT-04 R-CAT-04 search filters by name and price; paging is consistent and capped")
    void shouldSearchAndPaginateConsistently() throws Exception {
        String categoryId = createCategory();
        for (int i = 1; i <= 5; i++) {
            createProduct("PAG-00" + i, "Pagebook " + i, categoryId, String.valueOf(1000 + i * 100));
        }

        mvc.perform(get("/api/products/search").param("q", "pagebook").param("categoryId", categoryId)
                        .param("size", "2").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Pagebook 1"));
        mvc.perform(get("/api/products/search").param("q", "PAGEBOOK").param("categoryId", categoryId)
                        .param("size", "2").param("page", "2").param("sort", "name,asc"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Pagebook 5"));
        mvc.perform(get("/api/products/search").param("categoryId", categoryId).param("minPrice", "1300")
                        .param("maxPrice", "1400"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/products").param("size", "1000"))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/products").param("sort", "passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("R-CAT-01 PUT keeps the SKU immutable and records a new current price")
    void shouldUpdateProductWithoutChangingSku() throws Exception {
        String categoryId = createCategory();
        String id = createProduct("UPD-001", "Old Name", categoryId, "500.00");

        mvc.perform(put("/api/products/" + id).header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("UPD-999", "New Name", categoryId, "650.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("UPD-001"))
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.price.amount").value(650.00));
    }

    @Test
    @DisplayName("seeded catalog products exist with their fixed ids")
    void shouldServeSeededProducts() throws Exception {
        mvc.perform(get("/api/products/11111111-0000-0000-0000-000000000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("LAP-002"))
                .andExpect(jsonPath("$.price.amount").value(72999.99));
    }
}
