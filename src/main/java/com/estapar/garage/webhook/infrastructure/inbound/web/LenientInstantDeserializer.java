package com.estapar.garage.webhook.infrastructure.inbound.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Parses timestamps from both shapes the simulator emits (discovery §2.3): ISO-8601 with an
 * offset/{@code Z} ({@code 2025-01-01T12:00:00.000Z}) and the zoneless local-date-time
 * ({@code 2026-07-13T16:02:11}), which is interpreted as UTC (ADR-005). A blank or malformed
 * value yields {@code null}, letting the mapper's conditional validation produce a clean 400.
 */
public class LenientInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException withOffset) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException zoneless) {
                throw new InvalidTimestampException(value);
            }
        }
    }

    /** Signals an unparseable timestamp so the error layer can answer 400 (not 500). */
    static final class InvalidTimestampException extends IOException {
        InvalidTimestampException(String value) {
            super("Unparseable timestamp: '" + value + "'");
        }
    }
}
