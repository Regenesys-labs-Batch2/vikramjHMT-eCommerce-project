package com.happiestminds.ecom.catalog.service.exception;

import com.happiestminds.ecom.catalog.api.ApiError;

import java.util.List;

public class ConflictException extends DomainException {
    public ConflictException(String code, String message) {
        super(code, message, null);
    }

    public ConflictException(String code, String message, List<ApiError.FieldIssue> details) {
        super(code, message, details);
    }
}
