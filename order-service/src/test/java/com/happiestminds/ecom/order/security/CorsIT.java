package com.happiestminds.ecom.order.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIT {

    private static final String PATH = "/api/orders";
    private static final String UI_ORIGIN = "http://localhost:4200";

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("allows the preflight request with the Idempotency-Key header from the configured UI origin")
    void shouldAllowPreflight_whenOriginIsConfigured() throws Exception {
        mvc.perform(options(PATH).header("Origin", UI_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", UI_ORIGIN));
    }

    @Test
    @DisplayName("rejects the preflight request from an unknown origin")
    void shouldRejectPreflight_whenOriginIsUnknown() throws Exception {
        mvc.perform(options(PATH).header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS does not bypass authentication for the UI origin")
    void shouldReturn401_whenAnonymousPostFromAllowedOrigin() throws Exception {
        mvc.perform(post(PATH).header("Origin", UI_ORIGIN).header("Idempotency-Key", "k"))
                .andExpect(status().isUnauthorized());
    }
}
