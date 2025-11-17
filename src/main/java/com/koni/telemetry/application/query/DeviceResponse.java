package com.koni.telemetry.application.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Object representing a device with its latest temperature measurement.
 * This DTO is used in the query side (read model) to return device information to clients.
 * 
 * Contains:
 * - deviceId: unique identifier of the device
 * - measurement: the latest temperature measurement
 * - date: timestamp of the latest measurement
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    
    private Long deviceId;
    private BigDecimal measurement;
    private Instant date;
}
