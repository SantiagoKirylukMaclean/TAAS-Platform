package com.koni.telemetry.domain.repository;

import com.koni.telemetry.domain.model.DeviceProjection;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DeviceProjection persistence operations.
 * This interface is part of the domain layer and defines the contract
 * for device projection data access without coupling to specific infrastructure implementations.
 * 
 * Following Hexagonal Architecture principles, this interface will be implemented
 * by infrastructure adapters (e.g., JPA repositories).
 * 
 * The DeviceProjection represents the read model in the CQRS pattern, maintaining
 * the latest temperature measurement for each device.
 */
public interface DeviceProjectionRepository {
    
    /**
     * Finds a device projection by its device identifier.
     * This method is used by the event consumer to retrieve the current projection
     * before updating it with new telemetry data.
     * 
     * @param deviceId the unique identifier of the device
     * @return an Optional containing the DeviceProjection if found, or empty if not found
     * @throws IllegalArgumentException if deviceId is null
     */
    Optional<DeviceProjection> findByDeviceId(Long deviceId);
    
    /**
     * Persists or updates a device projection in the data store.
     * If a projection for the device already exists, it will be updated.
     * If not, a new projection will be created.
     * 
     * @param projection the device projection to save or update
     * @throws IllegalArgumentException if projection is null
     */
    void save(DeviceProjection projection);
    
    /**
     * Retrieves all device projections from the data store.
     * This method is used by the query side to return the list of all devices
     * with their latest temperature measurements.
     * 
     * @return a list of all DeviceProjection entities, or an empty list if none exist
     */
    List<DeviceProjection> findAll();
}
