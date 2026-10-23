package com.happiestminds.ecom.cart.client;

import com.happiestminds.ecom.cart.service.exception.DependencyUnavailableException;
import com.happiestminds.ecom.cart.service.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class CatalogClient {

    public record Price(BigDecimal amount, String currency) {
    }

    public record ProductInfo(String id, String sku, String name, boolean active, Price price) {
    }

    private final RestClient client;
    private final int retries;
    private final long backoffMs;

    public CatalogClient(RestClient.Builder builder,
                         @Value("${ecom.clients.catalog-url}") String baseUrl,
                         @Value("${ecom.clients.max-retries:2}") int retries,
                         @Value("${ecom.clients.retry-backoff-ms:500}") long backoffMs) {
        this.client = builder.baseUrl(baseUrl).build();
        this.retries = retries;
        this.backoffMs = backoffMs;
    }

    public ProductInfo getProduct(String productId) {
        return Retry.onUnavailable(retries, backoffMs, () -> {
            try {
                ProductInfo product = client.get().uri("/api/products/{id}", productId).retrieve()
                        .body(ProductInfo.class);
                if (product == null) {
                    throw new DependencyUnavailableException("Catalog returned an empty response");
                }
                return product;
            } catch (HttpClientErrorException.NotFound e) {
                throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found");
            } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
                throw new DependencyUnavailableException("Catalog service is unavailable");
            }
        });
    }
}
