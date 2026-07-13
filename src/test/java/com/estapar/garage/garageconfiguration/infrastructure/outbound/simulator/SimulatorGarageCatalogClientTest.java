package com.estapar.garage.garageconfiguration.infrastructure.outbound.simulator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.estapar.garage.garageconfiguration.application.GarageCatalog;
import com.estapar.garage.garageconfiguration.application.GarageCatalogFetchException;
import com.estapar.garage.shared.configuration.GarageProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the simulator adapter against a stubbed HTTP server: the real snake_case payload,
 * the document's camelCase alias, timeouts, connection failures, 500s, malformed JSON and the
 * structural validations that stop a bad catalog from reaching the domain. No real simulator.
 */
class SimulatorGarageCatalogClientTest {

    private WireMockServer wireMock;
    private SimulatorGarageCatalogClient client;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        client = buildClient(wireMock.baseUrl(), Duration.ofMillis(500));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    private SimulatorGarageCatalogClient buildClient(String baseUrl, Duration readTimeout) {
        var properties = new GarageProperties(
                java.time.ZoneId.of("America/Sao_Paulo"),
                new GarageProperties.Simulator(
                        baseUrl,
                        Duration.ofMillis(500),
                        readTimeout,
                        new GarageProperties.Simulator.Sync(1, Duration.ofMillis(1), Duration.ofMillis(1), false)));
        return new SimulatorGarageCatalogClient(new SimulatorClientConfiguration().simulatorRestClient(properties));
    }

    @Test
    void parsesRealSnakeCasePayloadWithExtraFields() {
        wireMock.stubFor(get(urlEqualTo("/garage"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "garage": [
                                    {"sector":"A","base_price":40.5,"max_capacity":10,
                                     "open_hour":"00:00","close_hour":"23:59","duration_limit_minutes":1440}
                                  ],
                                  "spots": [
                                    {"id":1,"sector":"A","lat":-23.561684,"lng":-46.655981,"occupied":false}
                                  ]
                                }
                                """)));

        GarageCatalog catalog = client.fetch();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(catalog.sectors()).hasSize(1);
            softly.assertThat(catalog.sectors().getFirst().basePrice().amount()).isEqualByComparingTo("40.50");
            softly.assertThat(catalog.sectors().getFirst().maxCapacity()).isEqualTo(10);
            softly.assertThat(catalog.spots()).hasSize(1);
            softly.assertThat(catalog.spots().getFirst().externalId()).isEqualTo(1L);
            softly.assertThat(catalog.totalCapacity()).isEqualTo(10);
        });
    }

    @Test
    void acceptsDocumentCamelCaseBasePriceAlias() {
        wireMock.stubFor(get(urlEqualTo("/garage"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"garage":[{"sector":"A","basePrice":10.0,"max_capacity":100}],
                                 "spots":[{"id":1,"sector":"A","lat":-23.561684,"lng":-46.655981}]}
                                """)));

        GarageCatalog catalog = client.fetch();

        assertThat(catalog.sectors().getFirst().basePrice().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void failsWhenServerReturns500() {
        wireMock.stubFor(get(urlEqualTo("/garage")).willReturn(aResponse().withStatus(500)));

        assertThatExceptionOfType(GarageCatalogFetchException.class).isThrownBy(() -> client.fetch());
    }

    @Test
    void failsOnMalformedJson() {
        wireMock.stubFor(get(urlEqualTo("/garage"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not valid json")));

        assertThatExceptionOfType(GarageCatalogFetchException.class).isThrownBy(() -> client.fetch());
    }

    @Test
    void failsOnReadTimeout() {
        var slowClient = buildClient(wireMock.baseUrl(), Duration.ofMillis(100));
        wireMock.stubFor(get(urlEqualTo("/garage"))
                .willReturn(aResponse()
                        .withFixedDelay(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"garage\":[],\"spots\":[]}")));

        assertThatExceptionOfType(GarageCatalogFetchException.class).isThrownBy(slowClient::fetch);
    }

    @Test
    void failsOnConnectionReset() {
        wireMock.stubFor(get(urlEqualTo("/garage")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatExceptionOfType(GarageCatalogFetchException.class).isThrownBy(() -> client.fetch());
    }

    @Test
    void rejectsCatalogWithNoSectors() {
        wireMock.stubFor(get(urlEqualTo("/garage"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"garage\":[],\"spots\":[{\"id\":1,\"sector\":\"A\",\"lat\":1,\"lng\":2}]}")));

        assertThatExceptionOfType(GarageCatalogFetchException.class).isThrownBy(() -> client.fetch());
    }

    @Test
    void rejectsSpotReferencingUnknownSector() {
        wireMock.stubFor(get(urlEqualTo("/garage"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"garage":[{"sector":"A","base_price":10,"max_capacity":5}],
                                 "spots":[{"id":1,"sector":"Z","lat":-23.5,"lng":-46.6}]}
                                """)));

        assertThatExceptionOfType(GarageCatalogFetchException.class).isThrownBy(() -> client.fetch());
    }

    @Test
    void rejectsDuplicatedSpotCoordinates() {
        wireMock.stubFor(get(urlEqualTo("/garage"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"garage":[{"sector":"A","base_price":10,"max_capacity":5}],
                                 "spots":[
                                   {"id":1,"sector":"A","lat":-23.561684,"lng":-46.655981},
                                   {"id":2,"sector":"A","lat":-23.561684,"lng":-46.655981}
                                 ]}
                                """)));

        assertThatExceptionOfType(GarageCatalogFetchException.class).isThrownBy(() -> client.fetch());
    }
}
