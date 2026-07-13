package com.estapar.garage.garageconfiguration.infrastructure.outbound.simulator;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Wire shape of the simulator's {@code GET /garage}. The real payload is snake_case with
 * extra fields (open/close hours, duration limit, occupied flag) — parsed leniently and
 * ignored where out of scope; {@code basePrice} alias covers the challenge document's
 * camelCase example. Evidence: docs/specs/00-challenge-analysis.md §2.2.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GarageResponse(List<SectorPayload> garage, List<SpotPayload> spots) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectorPayload(
            String sector,

            @JsonProperty("base_price") @JsonAlias("basePrice")
            BigDecimal basePrice,

            @JsonProperty("max_capacity") @JsonAlias("maxCapacity")
            Integer maxCapacity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotPayload(Long id, String sector, BigDecimal lat, BigDecimal lng) {}
}
