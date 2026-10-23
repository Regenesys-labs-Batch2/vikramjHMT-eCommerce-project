package com.happiestminds.ecom.cart.service.exception;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String code, String message) {
        super(code, message, null);
    }
}
