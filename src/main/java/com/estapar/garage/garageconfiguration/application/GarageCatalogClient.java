package com.estapar.garage.garageconfiguration.application;

/** Port: fetches the garage catalog from the simulator (adapter: outbound/simulator). */
public interface GarageCatalogClient {

    /**
     * One fetch attempt. Throws {@link GarageCatalogFetchException} on transport errors,
     * non-2xx responses or a payload that fails structural validation. Retrying is the
     * caller's decision, not the adapter's.
     */
    GarageCatalog fetch();
}
