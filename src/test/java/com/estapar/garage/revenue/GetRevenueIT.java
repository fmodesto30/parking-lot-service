package com.estapar.garage.revenue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.estapar.garage.garageconfiguration.domain.exception.SectorNotFoundException;
import com.estapar.garage.revenue.application.GetRevenueUseCase;
import com.estapar.garage.revenue.application.RevenueView;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Revenue aggregation with business-timezone date resolution (America/Sao_Paulo, UTC-3).
 * Charges are inserted at controlled UTC instants to prove the day-boundary conversion (ADR-005).
 */
class GetRevenueIT extends MySqlIntegrationTest {

    @Autowired
    private GetRevenueUseCase useCase;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        new GarageTestFixtures(jdbc).reset(10, 20);
    }

    private void charge(String sector, String amount, String chargedAtUtc) {
        // A charge needs a session row (FK). Create a minimal EXITED session to attach to.
        jdbc.update(
                "INSERT INTO parking_session(license_plate, active_plate, status, entry_time, exit_time,"
                        + " sector_code, version, created_at, updated_at) VALUES (?, NULL, 'EXITED', ?, ?, ?, 0, NOW(6), NOW(6))",
                "REV" + System.nanoTime() % 100000,
                chargedAtUtc,
                chargedAtUtc,
                sector);
        Long sessionId = jdbc.queryForObject("SELECT MAX(id) FROM parking_session", Long.class);
        jdbc.update(
                "INSERT INTO parking_charge(session_id, sector_code, amount, currency, charged_at, created_at)"
                        + " VALUES (?, ?, ?, 'BRL', ?, NOW(6))",
                sessionId,
                sector,
                new java.math.BigDecimal(amount),
                chargedAtUtc);
    }

    @Test
    void sumsChargesForSectorAndBusinessDate() {
        charge("A", "40.50", "2025-01-01T15:00:00");
        charge("A", "10.00", "2025-01-01T18:30:00");
        charge("B", "4.10", "2025-01-01T18:30:00");

        RevenueView revenue = useCase.execute("A", LocalDate.of(2025, 1, 1));

        assertThat(revenue.amount().amount()).isEqualByComparingTo("50.50");
        assertThat(revenue.amount().currency()).isEqualTo("BRL");
        assertThat(revenue.queriedAt()).isNotNull();
    }

    @Test
    void returnsZeroWhenNoChargesForThatDay() {
        RevenueView revenue = useCase.execute("A", LocalDate.of(2025, 1, 1));

        assertThat(revenue.amount().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void attributesLateNightUtcChargeToPreviousBusinessDay() {
        // 2025-01-02T02:30Z == 2025-01-01T23:30 in America/Sao_Paulo → business date 2025-01-01.
        charge("A", "25.00", "2025-01-02T02:30:00");

        assertThat(useCase.execute("A", LocalDate.of(2025, 1, 1)).amount().amount())
                .isEqualByComparingTo("25.00");
        assertThat(useCase.execute("A", LocalDate.of(2025, 1, 2)).amount().amount())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsUnknownSector() {
        assertThatThrownBy(() -> useCase.execute("Z", LocalDate.of(2025, 1, 1)))
                .isInstanceOf(SectorNotFoundException.class);
    }
}
