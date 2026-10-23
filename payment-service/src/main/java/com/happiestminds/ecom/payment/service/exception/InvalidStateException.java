package com.happiestminds.ecom.payment.service.exception;

public class InvalidStateException extends DomainException {
    public InvalidStateException(String code, String message) {
        super(code, message, null);
    }
}
