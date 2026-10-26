package com.happiestminds.ecom.catalog.security;

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

    private static final String PATH = "/api/products";
    private static final String UI_ORIGIN = "http://localhost:4200";

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("allows the preflight request from the configured UI origin without a token")
    void shouldAllowPreflight_whenOriginIsConfigured() throws Exception {
        mvc.perform(options(PATH).header("Origin", UI_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
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
    @DisplayName("AC-CAT-05 CORS does not let an anonymous caller from the UI origin modify the catalog")
    void shouldReturn401_whenAnonymousPostFromAllowedOrigin() throws Exception {
        mvc.perform(post(PATH).header("Origin", UI_ORIGIN).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
