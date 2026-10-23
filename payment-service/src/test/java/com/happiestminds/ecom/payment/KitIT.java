package com.happiestminds.ecom.payment;

import com.happiestminds.ecom.payment.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KitIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;

    @Test
    @DisplayName("health endpoint is public")
    void shouldReturnHealthWithoutToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("protected path without token returns the standard error body")
    void shouldReturn401WithApiError_whenNoToken() throws Exception {
        mvc.perform(get("/api/kit-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("invalid token is rejected with 401")
    void shouldReturn401_whenTokenIsInvalid() throws Exception {
        mvc.perform(get("/api/kit-probe").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("valid token passes the JWT filter")
    void shouldPassFilter_whenTokenIsValid() throws Exception {
        String token = jwtService.issue("user-1", "user@ecom.test", List.of("CUSTOMER"));

        mvc.perform(get("/api/kit-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
