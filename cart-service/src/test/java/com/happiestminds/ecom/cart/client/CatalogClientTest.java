package com.happiestminds.ecom.cart.client;

import com.happiestminds.ecom.cart.service.exception.DependencyUnavailableException;
import com.happiestminds.ecom.cart.service.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CatalogClient client = new CatalogClient(builder, "http://catalog", 0, 0);

    @Test
    @DisplayName("maps a Catalog product response")
    void shouldReturnProduct() {
        server.expect(requestTo("http://catalog/api/products/p1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"p1\",\"sku\":\"LAP-001\",\"name\":\"ProBook\",\"active\":true,"
                        + "\"price\":{\"amount\":55000.00,\"currency\":\"INR\"},\"extra\":1}", MediaType.APPLICATION_JSON));

        CatalogClient.ProductInfo product = client.getProduct("p1");

        assertThat(product.sku()).isEqualTo("LAP-001");
        assertThat(product.price().amount()).isEqualByComparingTo("55000.00");
    }

    @Test
    @DisplayName("maps 404 to PRODUCT_NOT_FOUND")
    void shouldMap404ToNotFound() {
        server.expect(requestTo("http://catalog/api/products/missing")).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.getProduct("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("maps a timeout and a 5xx to DependencyUnavailableException")
    void shouldMapTimeoutAndServerErrorToDependencyUnavailable() {
        server.expect(requestTo("http://catalog/api/products/slow")).andRespond(withException(new SocketTimeoutException()));
        server.expect(requestTo("http://catalog/api/products/broken")).andRespond(withServerError());

        assertThatThrownBy(() -> client.getProduct("slow")).isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> client.getProduct("broken")).isInstanceOf(DependencyUnavailableException.class);
    }
}
