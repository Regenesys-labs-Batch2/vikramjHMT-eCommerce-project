package com.happiestminds.ecom.inventory.service.exception;

public class DependencyUnavailableException extends DomainException {
    public DependencyUnavailableException(String message) {
        super("DEPENDENCY_UNAVAILABLE", message, null);
    }
}
