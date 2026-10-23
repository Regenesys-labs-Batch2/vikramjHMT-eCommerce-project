package com.happiestminds.ecom.identity.service.exception;

public class InvalidStateException extends DomainException {
    public InvalidStateException(String code, String message) {
        super(code, message, null);
    }
}
