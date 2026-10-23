package com.happiestminds.ecom.identity.service.exception;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, null);
    }
}
