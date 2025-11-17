package com.koni.telemetry.infrastructure.web.controller;

import com.koni.telemetry.application.command.RecordTelemetryCommand;
import com.koni.telemetry.application.command.RecordTelemetryCommandHandler;
import com.koni.telemetry.infrastructure.web.dto.TelemetryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for telemetry ingestion.
 * This controller handles the command side (write path) of the CQRS pattern.
 * 
 * Endpoints:
 * - POST /api/v1/telemetry: Accept telemetry data from devices
 * 
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class TelemetryController {
    
    private final RecordTelemetryCommandHandler commandHandler;
    
    /**
     * Accepts telemetry data from devices via HTTP POST.
     * 
     * The request body should contain:
     * - deviceId: unique identifier of the device (required)
     * - measurement: temperature measurement value (required)
     * - date: timestamp when measurement was taken in ISO 8601 format (required)
     * 
     * Example request:
     * POST /api/v1/telemetry
     * {
     *   "deviceId": 1,
     *   "measurement": 10.5,
     *   "date": "2025-01-31T13:00:00Z"
     * }
     * 
     * @param request the telemetry data to record
     * @return 202 Accepted on successful submission
     */
    @PostMapping("/v1/telemetry")
    public ResponseEntity<Void> recordTelemetry(@RequestBody @Valid TelemetryRequest request) {
        log.info("Received telemetry: deviceId={}, measurement={}, date={}", 
                request.getDeviceId(), request.getMeasurement(), request.getDate());
        
        // Create command from request
        RecordTelemetryCommand command = new RecordTelemetryCommand(
                request.getDeviceId(),
                request.getMeasurement(),
                request.getDate()
        );
        
        // Handle command
        commandHandler.handle(command);
        
        // Return 202 Accepted (asynchronous processing via Kafka)
        return ResponseEntity.accepted().build();
    }
}
