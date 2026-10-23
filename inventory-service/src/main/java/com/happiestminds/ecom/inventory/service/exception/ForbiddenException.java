package com.happiestminds.ecom.inventory.service.exception;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, null);
    }
}
