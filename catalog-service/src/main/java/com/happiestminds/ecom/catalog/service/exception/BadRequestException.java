package com.happiestminds.ecom.catalog.service.exception;

import com.happiestminds.ecom.catalog.api.ApiError;

import java.util.List;

public class BadRequestException extends DomainException {
    public BadRequestException(String code, String message) {
        super(code, message, null);
    }

    public BadRequestException(String code, String message, List<ApiError.FieldIssue> details) {
        super(code, message, details);
    }
}
