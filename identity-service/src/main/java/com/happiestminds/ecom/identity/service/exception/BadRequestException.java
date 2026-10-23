package com.happiestminds.ecom.identity.service.exception;

import com.happiestminds.ecom.identity.api.ApiError;

import java.util.List;

public class BadRequestException extends DomainException {
    public BadRequestException(String code, String message) {
        super(code, message, null);
    }

    public BadRequestException(String code, String message, List<ApiError.FieldIssue> details) {
        super(code, message, details);
    }
}
