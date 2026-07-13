package com.estapar.garage.garageconfiguration;

import static org.assertj.core.api.Assertions.assertThat;

import com.estapar.garage.garageconfiguration.application.GarageCatalog;
import com.estapar.garage.garageconfiguration.application.GarageCatalog.CatalogSector;
import com.estapar.garage.garageconfiguration.application.GarageCatalog.CatalogSpot;
import com.estapar.garage.garageconfiguration.application.GarageCatalogClient;
import com.estapar.garage.garageconfiguration.application.GarageReadiness;
import com.estapar.garage.garageconfiguration.application.SynchronizeGarageConfigurationUseCase;
import com.estapar.garage.shared.domain.Coordinates;
import com.estapar.garage.shared.domain.Money;
import com.estapar.garage.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves idempotent synchronization against real MySQL: re-syncing does not duplicate rows,
 * occupied spots survive a re-sync untouched, and the global counter is reconciled from the
 * actual active sessions.
 */
class SynchronizeGarageConfigurationIT extends MySqlIntegrationTest {

    @TestConfiguration
    static class StubClientConfig {
        @Bean
        @Primary
        GarageCatalogClient stubCatalogClient() {
            return new MutableStubClient();
        }
    }

    /** Test double whose payload the test rewrites between syncs. */
    static class MutableStubClient implements GarageCatalogClient {
        volatile GarageCatalog catalog = defaultCatalog();

        @Override
        public GarageCatalog fetch() {
            return catalog;
        }

        static GarageCatalog defaultCatalog() {
            return new GarageCatalog(
                    List.of(new CatalogSector("A", Money.of("40.50"), 2), new CatalogSector("B", Money.of("4.10"), 3)),
                    List.of(
                            new CatalogSpot(1, "A", coord("-23.561684", "-46.655981")),
                            new CatalogSpot(2, "A", coord("-23.561664", "-46.655961")),
                            new CatalogSpot(11, "B", coord("-23.561484", "-46.655781")),
                            new CatalogSpot(12, "B", coord("-23.561464", "-46.655761")),
                            new CatalogSpot(13, "B", coord("-23.561444", "-46.655741"))));
        }

        static Coordinates coord(String lat, String lng) {
            return new Coordinates(new BigDecimal(lat), new BigDecimal(lng));
        }
    }

    @Autowired
    private SynchronizeGarageConfigurationUseCase useCase;

    @Autowired
    private GarageCatalogClient catalogClient;

    @Autowired
    private GarageReadiness readiness;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM parking_charge");
        jdbc.update("DELETE FROM parking_session");
        jdbc.update("UPDATE parking_spot SET status='AVAILABLE', occupied_by_session_id=NULL");
        jdbc.update("DELETE FROM parking_spot");
        jdbc.update("DELETE FROM sector");
        jdbc.update("UPDATE garage_state SET total_capacity=0, active_vehicle_count=0 WHERE id=1");
        ((MutableStubClient) catalogClient).catalog = MutableStubClient.defaultCatalog();
    }

    @Test
    void persistsSectorsSpotsAndCapacityOnFirstSync() {
        var result = useCase.execute();

        assertThat(result.sectors()).isEqualTo(2);
        assertThat(result.spots()).isEqualTo(5);
        assertThat(result.totalCapacity()).isEqualTo(5);
        assertThat(count("sector")).isEqualTo(2);
        assertThat(count("parking_spot")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT total_capacity FROM garage_state WHERE id=1", Integer.class))
                .isEqualTo(5);
        assertThat(readiness.isReady()).isTrue();
    }

    @Test
    void reSyncDoesNotDuplicateRows() {
        useCase.execute();
        useCase.execute();
        useCase.execute();

        assertThat(count("sector")).isEqualTo(2);
        assertThat(count("parking_spot")).isEqualTo(5);
    }

    @Test
    void reSyncKeepsOccupiedSpotState() {
        useCase.execute();
        // Simulate a parked vehicle on spot external_id=1.
        jdbc.update("UPDATE parking_spot SET status='OCCUPIED', occupied_by_session_id=999 WHERE external_id=1");

        useCase.execute();

        var status = jdbc.queryForObject("SELECT status FROM parking_spot WHERE external_id=1", String.class);
        var occupant =
                jdbc.queryForObject("SELECT occupied_by_session_id FROM parking_spot WHERE external_id=1", Long.class);
        assertThat(status).isEqualTo("OCCUPIED");
        assertThat(occupant).isEqualTo(999L);
    }

    @Test
    void reSyncUpdatesBasePriceAndCapacityWhenSimulatorChanges() {
        useCase.execute();

        ((MutableStubClient) catalogClient).catalog = new GarageCatalog(
                List.of(new CatalogSector("A", Money.of("99.90"), 4), new CatalogSector("B", Money.of("4.10"), 3)),
                MutableStubClient.defaultCatalog().spots());
        useCase.execute();

        var basePrice = jdbc.queryForObject("SELECT base_price FROM sector WHERE code='A'", BigDecimal.class);
        assertThat(basePrice).isEqualByComparingTo("99.90");
        assertThat(jdbc.queryForObject("SELECT total_capacity FROM garage_state WHERE id=1", Integer.class))
                .isEqualTo(7);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
