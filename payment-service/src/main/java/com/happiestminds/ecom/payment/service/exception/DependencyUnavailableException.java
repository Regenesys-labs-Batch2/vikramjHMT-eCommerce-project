package com.happiestminds.ecom.payment.service.exception;

public class DependencyUnavailableException extends DomainException {
    public DependencyUnavailableException(String message) {
        super("DEPENDENCY_UNAVAILABLE", message, null);
    }
}
