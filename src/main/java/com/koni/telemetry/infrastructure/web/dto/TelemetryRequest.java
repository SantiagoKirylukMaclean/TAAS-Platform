package com.koni.telemetry.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Object for incoming telemetry data via REST API.
 * This DTO is used in the command side (write model) to receive telemetry submissions from clients.
 * 
 * Contains:
 * - deviceId: unique identifier of the device sending the measurement
 * - measurement: the temperature measurement value
 * - date: timestamp when the measurement was taken (ISO 8601 format)
 * 
 * All fields are required and validated using Jakarta Bean Validation annotations.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryRequest {
    
    @NotNull(message = "deviceId is required")
    private Long deviceId;
    
    @NotNull(message = "measurement is required")
    private BigDecimal measurement;
    
    @NotNull(message = "date is required")
    private Instant date;
}
