-- Secondary (non-unique) indexes for the hot query paths.
-- Locking access paths use PK / unique indexes created with the tables:
--   garage_state by PK, parking_spot by (latitude, longitude), parking_session by active_plate.

CREATE INDEX idx_parking_session_plate_status ON parking_session (license_plate, status);
CREATE INDEX idx_parking_session_status       ON parking_session (status);
CREATE INDEX idx_parking_charge_sector_date   ON parking_charge (sector_code, charged_at);
CREATE INDEX idx_parking_spot_sector_status   ON parking_spot (sector_code, status);
