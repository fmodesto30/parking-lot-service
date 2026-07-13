package com.estapar.garage.e2e;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.estapar.garage.garageconfiguration.application.GarageReadiness;
import com.estapar.garage.support.GarageTestFixtures;
import com.estapar.garage.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end through the real HTTP stack (mapper, filter, controllers, advice) and real MySQL:
 * the two canonical journeys — a charged stay and a free (≤30 min) stay — from ENTRY to the
 * revenue query. The simulator client is stubbed out; readiness is forced on so the webhook
 * accepts traffic.
 */
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class GarageLifecycleE2EIT extends MySqlIntegrationTest {

    private static final String SPOT_A1_LAT = "-23.561684";
    private static final String SPOT_A1_LNG = "-46.655981";

    /** Keep the startup sync from firing at the real simulator during the test. */
    @MockitoBean
    private com.estapar.garage.garageconfiguration.application.GarageCatalogClient catalogClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GarageReadiness readiness;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        new GarageTestFixtures(jdbc).reset(10, 20);
        readiness.markReady();
    }

    private void postWebhook(String body) throws Exception {
        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void chargedJourneyFromEntryToRevenue() throws Exception {
        postWebhook("""
                {"license_plate":"ZUL0001","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}""");
        postWebhook("""
                {"license_plate":"ZUL0001","lat":%s,"lng":%s,"event_type":"PARKED"}""".formatted(SPOT_A1_LAT, SPOT_A1_LNG));
        // 95 minutes → 2 billable hours; empty sector at PARKED → 0.90 × 40.50 = 36.45/h → 72.90
        postWebhook("""
                {"license_plate":"ZUL0001","exit_time":"2025-01-01T13:35:00.000Z","event_type":"EXIT"}""");

        mockMvc.perform(get("/revenue").param("date", "2025-01-01").param("sector", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(72.90))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void freeJourneyLeavesRevenueAtZero() throws Exception {
        postWebhook("""
                {"license_plate":"FREE001","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}""");
        postWebhook("""
                {"license_plate":"FREE001","lat":%s,"lng":%s,"event_type":"PARKED"}""".formatted(SPOT_A1_LAT, SPOT_A1_LNG));
        // Exactly 30 minutes → within the grace period → 0.00
        postWebhook("""
                {"license_plate":"FREE001","exit_time":"2025-01-01T12:30:00.000Z","event_type":"EXIT"}""");

        mockMvc.perform(get("/revenue").param("date", "2025-01-01").param("sector", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0.00));
    }

    @Test
    void duplicateEntryOverHttpIsAcknowledgedNotReapplied() throws Exception {
        String entry = """
                {"license_plate":"DUP0001","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}""";
        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(entry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PROCESSED"));
        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(entry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));
    }
}
