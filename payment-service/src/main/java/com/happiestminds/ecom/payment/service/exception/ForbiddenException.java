package com.happiestminds.ecom.payment.service.exception;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, null);
    }
}
