package com.happiestminds.ecom.identity.service.exception;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String code, String message) {
        super(code, message, null);
    }
}
