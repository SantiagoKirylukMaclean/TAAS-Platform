package com.koni.telemetry.infrastructure.persistence.repository;

import com.koni.telemetry.infrastructure.persistence.entity.TelemetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * JPA repository for TelemetryEntity persistence operations.
 * This repository provides database access for the write model in the CQRS pattern.
 * 
 * Spring Data JPA will automatically implement this interface at runtime,
 * providing standard CRUD operations and custom query methods.
 */
@Repository
public interface TelemetryJpaRepository extends JpaRepository<TelemetryEntity, Long> {
    
    /**
     * Checks if a telemetry record exists for the given device and timestamp.
     * This method is used for idempotency checks to prevent duplicate processing.
     * 
     * The unique constraint on (device_id, date) in the database ensures
     * that duplicate records cannot be inserted.
     * 
     * @param deviceId the unique identifier of the device
     * @param date the timestamp of the measurement
     * @return true if a telemetry record exists with the given deviceId and date, false otherwise
     */
    boolean existsByDeviceIdAndDate(Long deviceId, Instant date);
}
