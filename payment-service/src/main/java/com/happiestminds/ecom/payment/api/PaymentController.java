package com.happiestminds.ecom.payment.api;

import com.happiestminds.ecom.payment.api.dto.PaymentRequest;
import com.happiestminds.ecom.payment.api.dto.PaymentResponse;
import com.happiestminds.ecom.payment.api.dto.RefundRequest;
import com.happiestminds.ecom.payment.api.dto.RefundResponse;
import com.happiestminds.ecom.payment.security.AuthContext;
import com.happiestminds.ecom.payment.service.PaymentService;
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

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

    private static final String INTERNAL = "hasAnyRole('SERVICE','ADMIN')";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize(INTERNAL)
    public ResponseEntity<PaymentResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @Valid @RequestBody PaymentRequest request) {
        PaymentService.Result result = paymentService.create(idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable String id, Authentication auth) {
        return paymentService.get(id, AuthContext.subject(auth), AuthContext.isAdminOrService(auth));
    }

    @GetMapping
    public List<PaymentResponse> byOrder(@RequestParam String orderId, Authentication auth) {
        return paymentService.listByOrder(orderId, AuthContext.subject(auth), AuthContext.isAdminOrService(auth));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize(INTERNAL)
    public ResponseEntity<RefundResponse> refund(@PathVariable String id,
                                                 @Valid @RequestBody(required = false) RefundRequest request) {
        PaymentService.RefundResult result = paymentService.refund(id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }
}
