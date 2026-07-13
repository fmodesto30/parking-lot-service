package com.estapar.garage;

import static org.assertj.core.api.Assertions.assertThat;

import com.estapar.garage.support.MySqlIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationBootstrapIT extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesAllFlywayMigrationsOnPristineMySql() {
        List<Map<String, Object>> history =
                jdbc.queryForList("SELECT version, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).hasSize(7);
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
        assertThat(history)
                .extracting(row -> String.valueOf(row.get("version")))
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
    }

    @Test
    void seedsSingleGarageStateControlRow() {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT id, total_capacity, active_vehicle_count FROM garage_state");

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("total_capacity")).isEqualTo(0);
        assertThat(rows.getFirst().get("active_vehicle_count")).isEqualTo(0);
    }
}
