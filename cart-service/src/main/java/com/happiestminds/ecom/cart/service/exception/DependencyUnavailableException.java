package com.happiestminds.ecom.cart.service.exception;

public class DependencyUnavailableException extends DomainException {
    public DependencyUnavailableException(String message) {
        super("DEPENDENCY_UNAVAILABLE", message, null);
    }
}
