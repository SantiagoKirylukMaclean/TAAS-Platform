package com.koni.telemetry.infrastructure.web.controller;

import com.koni.telemetry.application.query.DeviceResponse;
import com.koni.telemetry.application.query.GetDevicesQuery;
import com.koni.telemetry.application.query.GetDevicesQueryHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for device queries.
 * This controller handles the query side (read path) of the CQRS pattern.
 * 
 * Endpoints:
 * - GET /api/devices: Retrieve all devices with their latest temperature measurements
 * 
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {
    
    private final GetDevicesQueryHandler queryHandler;
    
    /**
     * Retrieves all devices with their latest temperature measurements.
     * 
     * This endpoint queries the device projection (read model) to return
     * the current state of all devices. The projection is updated asynchronously
     * by the TelemetryEventConsumer when TelemetryRecorded events are processed.
     * 
     * Example response:
     * [
     *   {
     *     "deviceId": 1,
     *     "measurement": 12.0,
     *     "date": "2025-01-31T13:00:05Z"
     *   },
     *   {
     *     "deviceId": 2,
     *     "measurement": 15.5,
     *     "date": "2025-01-31T13:01:00Z"
     *   }
     * ]
     * 
     * @return 200 OK with a list of DeviceResponse objects (empty list if no devices exist)
     */
    @GetMapping("/v1/devices")
    public ResponseEntity<List<DeviceResponse>> getDevices() {
        log.info("Received request to get all devices");
        
        // Create query
        GetDevicesQuery query = new GetDevicesQuery();
        
        // Handle query
        List<DeviceResponse> devices = queryHandler.handle(query);
        
        log.info("Returning {} devices", devices.size());
        
        // Return 200 OK with device list (empty list is valid)
        return ResponseEntity.ok(devices);
    }
}
