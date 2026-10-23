package com.happiestminds.ecom.order.api;

import com.happiestminds.ecom.order.api.dto.CheckoutRequest;
import com.happiestminds.ecom.order.api.dto.OrderResponse;
import com.happiestminds.ecom.order.api.dto.PageResponse;
import com.happiestminds.ecom.order.security.AuthContext;
import com.happiestminds.ecom.order.service.CheckoutOrchestrator;
import com.happiestminds.ecom.order.service.OrderQueryService;
import com.happiestminds.ecom.order.service.exception.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final CheckoutOrchestrator orchestrator;
    private final OrderQueryService queries;

    public OrderController(CheckoutOrchestrator orchestrator, OrderQueryService queries) {
        this.orchestrator = orchestrator;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                @Valid @RequestBody(required = false) CheckoutRequest request,
                                                Authentication auth) {
        requireUuid(idempotencyKey);
        CheckoutOrchestrator.CheckoutResult result =
                orchestrator.checkout(AuthContext.subject(auth), idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable String id, Authentication auth) {
        return queries.viewFor(id, AuthContext.subject(auth), AuthContext.isAdminOrService(auth));
    }

    @GetMapping("/customer/{customerId}")
    public PageResponse<OrderResponse> byCustomer(@PathVariable String customerId,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  Authentication auth) {
        return queries.listFor(customerId, AuthContext.subject(auth), AuthContext.isAdminOrService(auth), page, size);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable String id, Authentication auth) {
        return orchestrator.cancel(id, AuthContext.subject(auth), AuthContext.isAdminOrService(auth));
    }

    @PostMapping("/{id}/recover")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    public OrderResponse recover(@PathVariable String id) {
        return orchestrator.recover(id);
    }

    private static void requireUuid(String key) {
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("VALIDATION_ERROR", "Idempotency-Key must be a UUID");
        }
    }
}
