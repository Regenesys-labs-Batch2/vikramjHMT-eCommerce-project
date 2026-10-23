package com.happiestminds.ecom.identity.api;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String error, String code, String message,
                       String path, String correlationId, List<FieldIssue> details) {

    public record FieldIssue(String field, String issue) {
    }

    public static ApiError of(HttpStatus status, String code, String message, String path, List<FieldIssue> details) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path,
                MDC.get("correlationId"), details == null ? List.of() : details);
    }
}
