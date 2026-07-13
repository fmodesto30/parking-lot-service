package com.estapar.garage.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes a correlation id per request: an inbound {@code X-Correlation-Id} is accepted
 * only if it is safe (bounded length, restricted charset — prevents log injection); otherwise a
 * UUID is generated. The id is placed on the MDC for every log line, echoed on the response,
 * and always removed in {@code finally}.
 */
@Component
@Order(CorrelationIdFilter.ORDER)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final int ORDER = -100;
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 64;
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = sanitizeOrGenerate(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String sanitizeOrGenerate(String candidate) {
        if (candidate != null
                && candidate.length() <= MAX_LENGTH
                && SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
