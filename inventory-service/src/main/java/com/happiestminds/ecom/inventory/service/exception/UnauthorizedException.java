package com.happiestminds.ecom.inventory.service.exception;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String code, String message) {
        super(code, message, null);
    }
}
