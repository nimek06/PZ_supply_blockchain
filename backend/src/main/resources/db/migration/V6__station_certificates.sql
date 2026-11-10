-- Week 2 continuation: station certificate storage for report verification.

CREATE TABLE station_certificates (
    certificate_id VARCHAR(66) PRIMARY KEY,
    station_address VARCHAR(42) NOT NULL,
    certificate_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_station_certificates_station
    ON station_certificates (station_address);
