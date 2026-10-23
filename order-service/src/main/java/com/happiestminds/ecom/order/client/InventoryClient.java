package com.happiestminds.ecom.order.client;

import com.happiestminds.ecom.order.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class InventoryClient {

    private static final String SERVICE = "Inventory service";

    public record Item(String productId, int quantity) {
    }

    public record Reservation(String reservationId, String status) {
    }

    private record ReserveRequest(String orderRef, List<Item> items) {
    }

    private final RestClient client;
    private final JwtService jwtService;
    private final int retries;
    private final long backoffMs;

    public InventoryClient(RestClient.Builder builder, JwtService jwtService,
                           @Value("${ecom.clients.inventory-url}") String baseUrl,
                           @Value("${ecom.clients.max-retries:2}") int retries,
                           @Value("${ecom.clients.retry-backoff-ms:500}") long backoffMs) {
        this.client = builder.baseUrl(baseUrl).build();
        this.jwtService = jwtService;
        this.retries = retries;
        this.backoffMs = backoffMs;
    }

    // Idempotent by orderRef on the Inventory side, but not retried: an unacknowledged hold simply expires.
    public Reservation reserve(String orderRef, List<Item> items) {
        return ClientErrors.call(SERVICE, () -> client.post().uri("/api/inventory/reservations")
                .header(HttpHeaders.AUTHORIZATION, serviceToken())
                .body(new ReserveRequest(orderRef, items))
                .retrieve().body(Reservation.class));
    }

    public Reservation confirm(String reservationId) {
        return Retry.onUnavailable(retries, backoffMs, () -> ClientErrors.call(SERVICE, () ->
                client.post().uri("/api/inventory/reservations/{id}/confirm", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, serviceToken())
                        .retrieve().body(Reservation.class)));
    }

    public Reservation release(String reservationId) {
        return Retry.onUnavailable(retries, backoffMs, () -> ClientErrors.call(SERVICE, () ->
                client.post().uri("/api/inventory/reservations/{id}/release", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, serviceToken())
                        .retrieve().body(Reservation.class)));
    }

    // Idempotent through the adjustment reference (for example cancel-<orderId>).
    public void restock(String productId, int quantity, String reference) {
        Retry.onUnavailable(retries, backoffMs, () -> ClientErrors.call(SERVICE, () -> {
            client.post().uri("/api/inventory/{productId}/adjustments", productId)
                    .header(HttpHeaders.AUTHORIZATION, serviceToken())
                    .body(Map.of("delta", quantity, "reason", "ORDER_CANCELLED", "reference", reference))
                    .retrieve().toBodilessEntity();
            return Boolean.TRUE;
        }));
    }

    private String serviceToken() {
        return "Bearer " + jwtService.issueServiceToken("order-service");
    }
}
