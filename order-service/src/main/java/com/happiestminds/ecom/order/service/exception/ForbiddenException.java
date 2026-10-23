package com.happiestminds.ecom.order.service.exception;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, null);
    }
}
