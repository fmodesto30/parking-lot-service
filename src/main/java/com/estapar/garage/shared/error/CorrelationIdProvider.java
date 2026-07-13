package com.estapar.garage.shared.error;

import com.estapar.garage.shared.observability.CorrelationIdFilter;
import org.slf4j.MDC;

/** Reads the current request's correlation id from the MDC for inclusion in error responses. */
final class CorrelationIdProvider {

    private CorrelationIdProvider() {}

    static String current() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value != null ? value : "unknown";
    }
}
