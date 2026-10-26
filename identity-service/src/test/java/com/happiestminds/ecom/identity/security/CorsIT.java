package com.happiestminds.ecom.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIT {

    private static final String PATH = "/api/users/me";
    private static final String UI_ORIGIN = "http://localhost:4200";

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("allows the preflight request from the configured UI origin without a token")
    void shouldAllowPreflight_whenOriginIsConfigured() throws Exception {
        mvc.perform(options(PATH).header("Origin", UI_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", UI_ORIGIN));
    }

    @Test
    @DisplayName("rejects the preflight request from an unknown origin")
    void shouldRejectPreflight_whenOriginIsUnknown() throws Exception {
        mvc.perform(options(PATH).header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS does not bypass authentication for the UI origin")
    void shouldReturn401_whenTokenMissingFromAllowedOrigin() throws Exception {
        mvc.perform(get(PATH).header("Origin", UI_ORIGIN))
                .andExpect(status().isUnauthorized());
    }
}
