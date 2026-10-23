package com.happiestminds.ecom.order.client;

import com.happiestminds.ecom.order.api.ApiError;
import com.happiestminds.ecom.order.service.exception.BadRequestException;
import com.happiestminds.ecom.order.service.exception.ConflictException;
import com.happiestminds.ecom.order.service.exception.DependencyUnavailableException;
import com.happiestminds.ecom.order.service.exception.IdempotencyConflictException;
import com.happiestminds.ecom.order.service.exception.InvalidStateException;
import com.happiestminds.ecom.order.service.exception.NotFoundException;
import com.happiestminds.ecom.order.service.exception.UnprocessableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.function.Supplier;

// Translates downstream failures into this service's domain exceptions.
final class ClientErrors {

    private static final Logger log = LoggerFactory.getLogger(ClientErrors.class);

    record Body(String code, String message, List<ApiError.FieldIssue> details) {
    }

    private ClientErrors() {
    }

    static <T> T call(String service, Supplier<T> action) {
        try {
            return action.get();
        } catch (HttpStatusCodeException e) {
            throw map(service, e);
        } catch (RestClientException e) {
            log.warn("Downstream call failed service={} error={}", service, e.getClass().getSimpleName());
            throw new DependencyUnavailableException(service + " is unavailable");
        }
    }

    static RuntimeException map(String service, HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        if (status >= 500) {
            log.warn("Downstream call failed service={} status={}", service, status);
            return new DependencyUnavailableException(service + " failed with status " + status);
        }
        Body body = null;
        try {
            body = e.getResponseBodyAs(Body.class);
        } catch (RuntimeException ignored) {
            // an unreadable error body falls back to a generic code below
        }
        String code = body != null && body.code() != null ? body.code() : "UPSTREAM_ERROR";
        log.warn("Downstream call rejected service={} status={} code={}", service, status, code);
        String message = body != null && body.message() != null ? body.message() : service + " rejected the request";
        List<ApiError.FieldIssue> details = body != null && body.details() != null ? body.details() : List.of();
        return switch (status) {
            case 400 -> new BadRequestException(code, message, details);
            case 404 -> new NotFoundException(code, message, details);
            case 409 -> code.endsWith("_STATE")
                    ? new InvalidStateException(code, message)
                    : new ConflictException(code, message, details);
            case 422 -> "IDEMPOTENCY_CONFLICT".equals(code)
                    ? new IdempotencyConflictException(code, message)
                    : new UnprocessableException(code, message);
            default -> new DependencyUnavailableException(service + " rejected the request with status " + status);
        };
    }
}
