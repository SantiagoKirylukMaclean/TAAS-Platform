package com.koni.telemetry.domain.repository;

import com.koni.telemetry.domain.model.Telemetry;

import java.time.Instant;

/**
 * Repository interface for Telemetry persistence operations.
 * This interface is part of the domain layer and defines the contract
 * for telemetry data access without coupling to specific infrastructure implementations.
 * 
 * Following Hexagonal Architecture principles, this interface will be implemented
 * by infrastructure adapters (e.g., JPA repositories).
 */
public interface TelemetryRepository {
    
    /**
     * Persists a telemetry measurement to the data store.
     * 
     * @param telemetry the telemetry value object to save
     * @throws IllegalArgumentException if telemetry is null
     */
    void save(Telemetry telemetry);
    
    /**
     * Checks if a telemetry record already exists for the given device and timestamp.
     * This method is used for idempotency checks to prevent duplicate processing
     * of the same telemetry data.
     * 
     * @param deviceId the unique identifier of the device
     * @param date the timestamp of the measurement
     * @return true if a telemetry record exists with the given deviceId and date, false otherwise
     * @throws IllegalArgumentException if deviceId or date is null
     */
    boolean exists(Long deviceId, Instant date);
}
