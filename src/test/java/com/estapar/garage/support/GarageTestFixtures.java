package com.estapar.garage.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeds a known garage directly in MySQL for parking integration tests, bypassing the
 * simulator. Two sectors matching the real payload shape: A (base 40.50) and B (base 4.10),
 * with caller-chosen capacities and enough distinct spots to exercise occupancy.
 */
public final class GarageTestFixtures {

    private final JdbcTemplate jdbc;

    public GarageTestFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Wipes all parking data and reseeds sectors A/B, spots and the global capacity row. */
    public void reset(int capacityA, int capacityB) {
        jdbc.update("DELETE FROM processed_webhook_event");
        jdbc.update("DELETE FROM parking_charge");
        jdbc.update("DELETE FROM parking_session");
        jdbc.update("DELETE FROM parking_spot");
        jdbc.update("DELETE FROM sector");

        // max_capacity must be > 0 (schema CHECK), so a sector with zero capacity is simply omitted.
        if (capacityA > 0) {
            jdbc.update(
                    "INSERT INTO sector(code, base_price, max_capacity, created_at, updated_at) "
                            + "VALUES ('A', 40.50, ?, NOW(6), NOW(6))",
                    capacityA);
            seedSpots("A", capacityA, -23.561684, -46.655981, 1);
        }
        if (capacityB > 0) {
            jdbc.update(
                    "INSERT INTO sector(code, base_price, max_capacity, created_at, updated_at) "
                            + "VALUES ('B', 4.10, ?, NOW(6), NOW(6))",
                    capacityB);
            seedSpots("B", capacityB, -23.561484, -46.655781, 1000);
        }

        jdbc.update(
                "UPDATE garage_state SET total_capacity=?, active_vehicle_count=0, "
                        + "last_synchronized_at=NOW(6) WHERE id=1",
                capacityA + capacityB);
    }

    /** Coordinates of the n-th spot (1-based) of a sector, matching the seeding stride. */
    public double[] spotCoordinates(String sector, int index) {
        double baseLat = sector.equals("A") ? -23.561684 : -23.561484;
        double baseLng = sector.equals("A") ? -46.655981 : -46.655781;
        return new double[] {round6(baseLat + (index - 1) * 0.00002), round6(baseLng + (index - 1) * 0.00002)};
    }

    private void seedSpots(String sector, int count, double baseLat, double baseLng, int externalIdBase) {
        for (int i = 0; i < count; i++) {
            jdbc.update(
                    "INSERT INTO parking_spot(external_id, sector_code, latitude, longitude, status, version, "
                            + "created_at, updated_at) VALUES (?, ?, ?, ?, 'AVAILABLE', 0, NOW(6), NOW(6))",
                    externalIdBase + i,
                    sector,
                    round6(baseLat + i * 0.00002),
                    round6(baseLng + i * 0.00002));
        }
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
