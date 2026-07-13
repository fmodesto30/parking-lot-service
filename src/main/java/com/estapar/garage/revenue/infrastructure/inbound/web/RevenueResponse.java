package com.estapar.garage.revenue.infrastructure.inbound.web;

import com.estapar.garage.revenue.application.RevenueView;
import java.math.BigDecimal;
import java.time.Instant;

/** Revenue query response (spec 03): amount, fixed BRL currency, and the query timestamp. */
public record RevenueResponse(BigDecimal amount, String currency, Instant timestamp) {

    public static RevenueResponse from(RevenueView view) {
        return new RevenueResponse(view.amount().amount(), view.amount().currency(), view.queriedAt());
    }
}
