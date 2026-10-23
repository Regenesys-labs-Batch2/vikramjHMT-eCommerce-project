package com.happiestminds.ecom.inventory.service.exception;

public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException(String code, String message) {
        super(code, message, null);
    }
}
