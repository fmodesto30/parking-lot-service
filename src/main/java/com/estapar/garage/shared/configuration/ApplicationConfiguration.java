package com.estapar.garage.shared.configuration;

import com.estapar.garage.pricing.domain.BillingCalculator;
import com.estapar.garage.pricing.domain.OccupancyPricingPolicy;
import com.estapar.garage.pricing.domain.TieredOccupancyPricingPolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Spring-free domain services into the container. The domain classes themselves
 * carry no framework annotations (ADR-001 boundaries, enforced by ArchUnit).
 */
@Configuration
public class ApplicationConfiguration {

    /** Single time source; injected everywhere so tests can pin the clock deterministically. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OccupancyPricingPolicy occupancyPricingPolicy() {
        return new TieredOccupancyPricingPolicy();
    }

    @Bean
    public BillingCalculator billingCalculator() {
        return new BillingCalculator();
    }
}
