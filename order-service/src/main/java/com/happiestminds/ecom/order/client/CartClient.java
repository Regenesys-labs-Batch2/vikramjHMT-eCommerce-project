package com.happiestminds.ecom.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import com.happiestminds.ecom.order.security.JwtService;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class CartClient {

    private static final String SERVICE = "Cart service";

    public record Item(String productId, String sku, String productName, int quantity, BigDecimal unitPrice,
                       BigDecimal lineTotal) {
    }

    public record CheckoutSummary(String cartId, String customerId, List<Item> items, BigDecimal subtotal,
                                  String discountCode, BigDecimal discountAmount, BigDecimal total, String currency) {
    }

    private final RestClient client;
    private final JwtService jwtService;
    private final int retries;
    private final long backoffMs;

    public CartClient(RestClient.Builder builder, JwtService jwtService,
                      @Value("${ecom.clients.cart-url}") String baseUrl,
                      @Value("${ecom.clients.max-retries:2}") int retries,
                      @Value("${ecom.clients.retry-backoff-ms:500}") long backoffMs) {
        this.client = builder.baseUrl(baseUrl).build();
        this.jwtService = jwtService;
        this.retries = retries;
        this.backoffMs = backoffMs;
    }

    // The customer's own bearer token is forwarded by the shared RestClient interceptor (the cart is theirs).
    // An explicit JSON body avoids the JDK client defaulting to a form content type, which Cart rejects.
    public CheckoutSummary checkoutSummary() {
        return ClientErrors.call(SERVICE, () -> client.post().uri("/api/carts/checkout-summary")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of())
                .retrieve().body(CheckoutSummary.class));
    }

    // Best effort and idempotent; uses a service token because there may be no customer context.
    public void markCheckedOut(String cartId) {
        Retry.onUnavailable(retries, backoffMs, () -> ClientErrors.call(SERVICE, () -> {
            client.post().uri("/api/carts/{id}/checked-out", cartId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken("order-service"))
                    .retrieve().toBodilessEntity();
            return Boolean.TRUE;
        }));
    }
}
