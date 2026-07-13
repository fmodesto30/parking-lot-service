package com.estapar.garage.shared.configuration;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed view of the {@code garage.*} configuration (see application.yml / .env.example). */
@Validated
@ConfigurationProperties(prefix = "garage")
public record GarageProperties(ZoneId businessZone, Simulator simulator) {

    public record Simulator(String baseUrl, Duration connectTimeout, Duration readTimeout, Sync sync) {

        public record Sync(int maxAttempts, Duration initialBackoff, Duration maxBackoff, boolean onStartup) {}
    }
}
