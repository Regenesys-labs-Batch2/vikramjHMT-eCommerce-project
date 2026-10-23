package com.happiestminds.ecom.order.client;

import com.happiestminds.ecom.order.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentClient {

    private static final String SERVICE = "Payment service";

    public record Payment(String id, String status, String failureReason) {
    }

    public record Refund(String refundId, String status) {
    }

    private final RestClient client;
    private final JwtService jwtService;
    private final int retries;
    private final long backoffMs;

    public PaymentClient(RestClient.Builder builder, JwtService jwtService,
                         @Value("${ecom.clients.payment-url}") String baseUrl,
                         @Value("${ecom.clients.max-retries:2}") int retries,
                         @Value("${ecom.clients.retry-backoff-ms:500}") long backoffMs) {
        this.client = builder.baseUrl(baseUrl).build();
        this.jwtService = jwtService;
        this.retries = retries;
        this.backoffMs = backoffMs;
    }

    // Never retried here: after a timeout the order is reconciled by repeating this call with the same key
    // (the Idempotency-Key is the order id), which returns the original transaction instead of charging twice.
    public Payment create(String orderId, String customerId, BigDecimal amount, String currency,
                          String simulatedOutcome) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("customerId", customerId);
        body.put("amount", amount);
        body.put("currency", currency);
        if (simulatedOutcome != null) {
            body.put("simulatedOutcome", simulatedOutcome);
        }
        return ClientErrors.call(SERVICE, () -> client.post().uri("/api/payments")
                .header(HttpHeaders.AUTHORIZATION, serviceToken())
                .header("Idempotency-Key", orderId)
                .body(body)
                .retrieve().body(Payment.class));
    }

    public Refund refund(String paymentId, String reason) {
        return Retry.onUnavailable(retries, backoffMs, () -> ClientErrors.call(SERVICE, () ->
                client.post().uri("/api/payments/{id}/refund", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, serviceToken())
                        .body(Map.of("reason", reason))
                        .retrieve().body(Refund.class)));
    }

    private String serviceToken() {
        return "Bearer " + jwtService.issueServiceToken("order-service");
    }
}
