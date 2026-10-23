package com.happiestminds.ecom.payment.service.exception;

import com.happiestminds.ecom.payment.api.ApiError;

import java.util.List;

public class ConflictException extends DomainException {
    public ConflictException(String code, String message) {
        super(code, message, null);
    }

    public ConflictException(String code, String message, List<ApiError.FieldIssue> details) {
        super(code, message, details);
    }
}
