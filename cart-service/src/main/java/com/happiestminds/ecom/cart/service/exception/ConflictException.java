package com.happiestminds.ecom.cart.service.exception;

import com.happiestminds.ecom.cart.api.ApiError;

import java.util.List;

public class ConflictException extends DomainException {
    public ConflictException(String code, String message) {
        super(code, message, null);
    }

    public ConflictException(String code, String message, List<ApiError.FieldIssue> details) {
        super(code, message, details);
    }
}
