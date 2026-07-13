package com.estapar.garage.revenue.application;

import com.estapar.garage.shared.domain.Money;
import java.time.Instant;

/** Result of a revenue query: the summed amount, its currency, and when it was computed. */
public record RevenueView(Money amount, Instant queriedAt) {}
