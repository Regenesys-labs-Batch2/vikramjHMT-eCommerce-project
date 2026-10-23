package com.happiestminds.ecom.catalog.service.exception;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, null);
    }
}
