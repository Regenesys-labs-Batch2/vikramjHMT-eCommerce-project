package com.happiestminds.ecom.inventory.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // Prototype: each client calls baseUrl() on its own builder instance.
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder restClientBuilder(@Value("${ecom.clients.connect-timeout-ms:1000}") int connectTimeoutMs,
                                         @Value("${ecom.clients.read-timeout-ms:2000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(propagateContext());
    }

    private static ClientHttpRequestInterceptor propagateContext() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
            }
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)
                    && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String auth = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (auth != null) {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, auth);
                }
            }
            return execution.execute(request, body);
        };
    }
}
