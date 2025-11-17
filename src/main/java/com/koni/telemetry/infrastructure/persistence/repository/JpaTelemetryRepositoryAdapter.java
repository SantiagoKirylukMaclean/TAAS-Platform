package com.koni.telemetry.infrastructure.persistence.repository;

import com.koni.telemetry.domain.model.Telemetry;
import com.koni.telemetry.domain.repository.TelemetryRepository;
import com.koni.telemetry.infrastructure.persistence.entity.TelemetryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * JPA adapter for TelemetryRepository that adapts the domain interface
 * to the JPA infrastructure layer.
 * 
 * This adapter follows Hexagonal Architecture principles by implementing
 * the domain repository interface and delegating to the JPA repository.
 * It handles mapping between domain models (Telemetry) and JPA entities (TelemetryEntity).
 */
@Component
@RequiredArgsConstructor
public class JpaTelemetryRepositoryAdapter implements TelemetryRepository {
    
    private final TelemetryJpaRepository jpaRepository;
    
    /**
     * Persists a telemetry measurement to the database.
     * Maps the domain Telemetry value object to a TelemetryEntity and saves it.
     * 
     * @param telemetry the telemetry value object to save
     * @throws IllegalArgumentException if telemetry is null
     */
    @Override
    public void save(Telemetry telemetry) {
        if (telemetry == null) {
            throw new IllegalArgumentException("Telemetry cannot be null");
        }
        
        TelemetryEntity entity = toEntity(telemetry);
        jpaRepository.save(entity);
    }
    
    /**
     * Checks if a telemetry record already exists for the given device and timestamp.
     * 
     * @param deviceId the unique identifier of the device
     * @param date the timestamp of the measurement
     * @return true if a telemetry record exists with the given deviceId and date, false otherwise
     * @throws IllegalArgumentException if deviceId or date is null
     */
    @Override
    public boolean exists(Long deviceId, Instant date) {
        if (deviceId == null) {
            throw new IllegalArgumentException("DeviceId cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        
        return jpaRepository.existsByDeviceIdAndDate(deviceId, date);
    }
    
    /**
     * Maps a domain Telemetry value object to a TelemetryEntity.
     * 
     * @param telemetry the domain model
     * @return the corresponding JPA entity
     */
    private TelemetryEntity toEntity(Telemetry telemetry) {
        return new TelemetryEntity(
            telemetry.getDeviceId(),
            telemetry.getMeasurement(),
            telemetry.getDate(),
            Instant.now()
        );
    }
}
