package com.happiestminds.ecom.cart.service.exception;

import com.happiestminds.ecom.cart.api.ApiError;

import java.util.List;

public abstract class DomainException extends RuntimeException {

    private final String code;
    private final List<ApiError.FieldIssue> details;

    protected DomainException(String code, String message, List<ApiError.FieldIssue> details) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : details;
    }

    public String getCode() {
        return code;
    }

    public List<ApiError.FieldIssue> getDetails() {
        return details;
    }
}
