package com.estapar.garage.garageconfiguration.application;

/** The simulator could not be reached or returned an unusable payload. */
public class GarageCatalogFetchException extends RuntimeException {

    public GarageCatalogFetchException(String message) {
        super(message);
    }

    public GarageCatalogFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
