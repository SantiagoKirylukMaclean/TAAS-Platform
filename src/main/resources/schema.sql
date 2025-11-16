-- Telemetry table (Write Model - Command Side)
-- Stores all received telemetry records with idempotency constraint
CREATE TABLE IF NOT EXISTS telemetry (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    measurement DECIMAL(10, 2) NOT NULL,
    date TIMESTAMP NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_device_date UNIQUE (device_id, date)
);

-- Index for fast lookups by device and date
CREATE INDEX IF NOT EXISTS idx_telemetry_device_date ON telemetry(device_id, date DESC);

-- Device Projection table (Read Model - Query Side)
-- Maintains the latest temperature measurement per device
CREATE TABLE IF NOT EXISTS device_projection (
    device_id BIGINT PRIMARY KEY,
    latest_measurement DECIMAL(10, 2) NOT NULL,
    latest_date TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for sorting by latest date
CREATE INDEX IF NOT EXISTS idx_device_projection_date ON device_projection(latest_date DESC);
