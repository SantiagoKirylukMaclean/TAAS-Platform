package com.koni.telemetry.infrastructure.persistence.repository;

import com.koni.telemetry.infrastructure.persistence.entity.DeviceProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for DeviceProjectionEntity persistence operations.
 * This repository provides database access for the read model in the CQRS pattern.
 * 
 * Spring Data JPA will automatically implement this interface at runtime,
 * providing standard CRUD operations and custom query methods.
 * 
 * The DeviceProjection maintains the latest temperature measurement for each device,
 * updated asynchronously by the event consumer.
 */
@Repository
public interface DeviceProjectionJpaRepository extends JpaRepository<DeviceProjectionEntity, Long> {
    
    /**
     * Finds a device projection by its device identifier.
     * 
     * Since deviceId is the primary key of DeviceProjectionEntity,
     * this method is equivalent to findById() but provides a more
     * domain-meaningful method name.
     * 
     * @param deviceId the unique identifier of the device
     * @return an Optional containing the DeviceProjectionEntity if found, or empty if not found
     */
    Optional<DeviceProjectionEntity> findByDeviceId(Long deviceId);
}
