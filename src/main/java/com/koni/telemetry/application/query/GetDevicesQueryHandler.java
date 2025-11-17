package com.koni.telemetry.application.query;

import com.koni.telemetry.domain.repository.DeviceProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Query handler for retrieving all devices with their latest temperature measurements.
 * This handler implements the read side of the CQRS pattern.
 * 
 * Responsibilities:
 * - Query all device projections from the repository
 * - Map domain models to DeviceResponse DTOs
 * - Handle empty results gracefully
 * 
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetDevicesQueryHandler {
    
    private final DeviceProjectionRepository repository;
    
    /**
     * Handles the GetDevicesQuery by retrieving all device projections
     * and mapping them to DeviceResponse DTOs.
     * 
     * This method returns an empty list if no devices exist, which is a valid state.
     * 
     * @param query the query object (contains no parameters)
     * @return a list of DeviceResponse objects, or an empty list if no devices exist
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> handle(GetDevicesQuery query) {
        log.debug("Handling GetDevicesQuery");
        
        List<DeviceResponse> devices = repository.findAll().stream()
                .map(projection -> new DeviceResponse(
                        projection.getDeviceId(),
                        projection.getLatestMeasurement(),
                        projection.getLatestDate()
                ))
                .collect(Collectors.toList());
        
        log.info("Retrieved {} devices", devices.size());
        
        return devices;
    }
}
