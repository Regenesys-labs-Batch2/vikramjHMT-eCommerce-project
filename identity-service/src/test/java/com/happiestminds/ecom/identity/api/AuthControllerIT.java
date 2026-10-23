package com.happiestminds.ecom.identity.api;

import com.jayway.jsonpath.JsonPath;
import com.happiestminds.ecom.identity.security.JwtService;
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
class AuthControllerIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\",\"fullName\":\"Test User\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String unique() {
        return "user-" + UUID.randomUUID() + "@ecom.test";
    }

    @Test
    @DisplayName("AC-IDN-01 R-IDN-01 duplicate registration is rejected with 409")
    void shouldReturn409_whenEmailAlreadyRegistered() throws Exception {
        String email = unique();
        register(email);

        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"password1\",\"fullName\":\"X\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("R-IDN-02 short password and invalid email return 400 with details")
    void shouldReturn400_whenRegistrationIsInvalid() throws Exception {
        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isNotEmpty());
    }

    @Test
    @DisplayName("AC-IDN-02 wrong credentials return a generic 401")
    void shouldReturn401_whenCredentialsAreWrong() throws Exception {
        String email = unique();
        register(email);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@ecom.test\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("AC-IDN-04 profile can be read and updated only for the token subject")
    void shouldReadAndUpdateOwnProfile() throws Exception {
        String email = unique();
        register(email);
        String token = login(email, "password1");

        mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"phone\":\"+91 98765 43210\",\"addressLine1\":\"1 Main St\","
                                + "\"city\":\"Bengaluru\",\"postcode\":\"560001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.fullName").value("New Name"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.profile.city").value("Bengaluru"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("AC-IDN-03 requests without or with an expired token are rejected")
    void shouldReturn401_whenTokenMissingOrInvalid() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-IDN-05 R-IDN-03 customers cannot use the admin lookup; admins can")
    void shouldProtectAdminLookup() throws Exception {
        String customerId = register(unique());
        String customerToken = jwtService.issue(customerId, "c@ecom.test", List.of("CUSTOMER"));

        mvc.perform(get("/api/users/" + customerId).header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        String adminToken = login("admin@ecom.test", "Admin#12345");
        mvc.perform(get("/api/users/" + customerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId));
        mvc.perform(get("/api/users/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
