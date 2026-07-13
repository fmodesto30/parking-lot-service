package com.estapar.garage.shared.domain;

/** Base type for business-rule violations. Carried untouched to the error layer for HTTP mapping. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
