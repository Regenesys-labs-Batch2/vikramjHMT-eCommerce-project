package com.happiestminds.ecom.payment.service.exception;

public class UnprocessableException extends DomainException {
    public UnprocessableException(String code, String message) {
        super(code, message, null);
    }
}
