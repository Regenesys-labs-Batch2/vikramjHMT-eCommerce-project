package com.happiestminds.ecom.payment.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("unique-key race is mapped to 409 CONFLICT instead of 500")
    void shouldReturnConflict_whenDataIntegrityIsViolated() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");

        ResponseEntity<ApiError> response = handler.handleIntegrity(new DataIntegrityViolationException("dup"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
        assertThat(response.getBody().path()).isEqualTo("/api/payments");
    }

    @Test
    @DisplayName("optimistic-lock failure is mapped to 409 CONCURRENT_UPDATE instead of 500")
    void shouldReturnConflict_whenOptimisticLockFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");

        ResponseEntity<ApiError> response = handler.handleConcurrentUpdate(
                new ObjectOptimisticLockingFailureException(Object.class, "payment-1"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
    }
}
