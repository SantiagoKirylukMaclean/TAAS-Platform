package com.koni.telemetry.infrastructure.persistence.repository;

import com.koni.telemetry.domain.model.DeviceProjection;
import com.koni.telemetry.domain.repository.DeviceProjectionRepository;
import com.koni.telemetry.infrastructure.persistence.entity.DeviceProjectionEntity;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.ContinueSpan;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA adapter for DeviceProjectionRepository that adapts the domain interface
 * to the JPA infrastructure layer.
 * 
 * This adapter follows Hexagonal Architecture principles by implementing
 * the domain repository interface and delegating to the JPA repository.
 * It handles mapping between domain models (DeviceProjection) and JPA entities (DeviceProjectionEntity).
 */
@Component
@RequiredArgsConstructor
public class JpaDeviceProjectionRepositoryAdapter implements DeviceProjectionRepository {
    
    private final DeviceProjectionJpaRepository jpaRepository;
    
    /**
     * Finds a device projection by its device identifier.
     * Maps the JPA entity to a domain model if found.
     * 
     * @param deviceId the unique identifier of the device
     * @return an Optional containing the DeviceProjection if found, or empty if not found
     * @throws IllegalArgumentException if deviceId is null
     */
    @Override
    @ContinueSpan(log = "device-projection-find")
    public Optional<DeviceProjection> findByDeviceId(@SpanTag("deviceId") Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("DeviceId cannot be null");
        }
        
        return jpaRepository.findByDeviceId(deviceId)
            .map(this::toDomain);
    }
    
    /**
     * Persists or updates a device projection in the database.
     * Maps the domain model to a JPA entity and saves it.
     * 
     * @param projection the device projection to save or update
     * @throws IllegalArgumentException if projection is null
     */
    @Override
    @ContinueSpan(log = "device-projection-save")
    public void save(@SpanTag("deviceId") DeviceProjection projection) {
        if (projection == null) {
            throw new IllegalArgumentException("DeviceProjection cannot be null");
        }
        
        DeviceProjectionEntity entity = toEntity(projection);
        jpaRepository.save(entity);
    }
    
    /**
     * Retrieves all device projections from the database.
     * Maps all JPA entities to domain models.
     * 
     * @return a list of all DeviceProjection entities, or an empty list if none exist
     */
    @Override
    @ContinueSpan(log = "device-projection-findAll")
    public List<DeviceProjection> findAll() {
        return jpaRepository.findAll().stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }
    
    /**
     * Maps a JPA DeviceProjectionEntity to a domain DeviceProjection model.
     * 
     * @param entity the JPA entity
     * @return the corresponding domain model
     */
    private DeviceProjection toDomain(DeviceProjectionEntity entity) {
        return new DeviceProjection(
            entity.getDeviceId(),
            entity.getLatestMeasurement(),
            entity.getLatestDate()
        );
    }
    
    /**
     * Maps a domain DeviceProjection model to a JPA DeviceProjectionEntity.
     * 
     * @param projection the domain model
     * @return the corresponding JPA entity
     */
    private DeviceProjectionEntity toEntity(DeviceProjection projection) {
        return new DeviceProjectionEntity(
            projection.getDeviceId(),
            projection.getLatestMeasurement(),
            projection.getLatestDate()
        );
    }
}
