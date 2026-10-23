package com.happiestminds.ecom.order.service.exception;

import com.happiestminds.ecom.order.api.ApiError;

import java.util.List;

public class NotFoundException extends DomainException {
    public NotFoundException(String code, String message) {
        super(code, message, null);
    }

    public NotFoundException(String code, String message, List<ApiError.FieldIssue> details) {
        super(code, message, details);
    }
}
