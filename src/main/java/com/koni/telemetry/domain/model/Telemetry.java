package com.koni.telemetry.domain.model;

import com.koni.telemetry.domain.exception.ValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Telemetry value object representing a temperature measurement from a device.
 * This is an immutable value object that encapsulates telemetry data with validation.
 */
@Getter
@EqualsAndHashCode(of = {"deviceId", "date"})
public class Telemetry {
    
    private final Long deviceId;
    private final BigDecimal measurement;
    private final Instant date;
    
    /**
     * Creates a new Telemetry value object.
     *
     * @param deviceId the unique identifier of the device
     * @param measurement the temperature measurement value
     * @param date the timestamp when the measurement was taken
     */
    public Telemetry(Long deviceId, BigDecimal measurement, Instant date) {
        this.deviceId = deviceId;
        this.measurement = measurement;
        this.date = date;
    }
    
    /**
     * Validates the telemetry data according to business rules.
     * 
     * @throws ValidationException if validation fails
     */
    public void validate() {
        if (deviceId == null) {
            throw new ValidationException("deviceId is required");
        }
        if (measurement == null) {
            throw new ValidationException("measurement is required");
        }
        if (date == null) {
            throw new ValidationException("date is required");
        }
        if (date.isAfter(Instant.now())) {
            throw new ValidationException("date cannot be in the future");
        }
    }
    
    @Override
    public String toString() {
        return "Telemetry{" +
                "deviceId=" + deviceId +
                ", measurement=" + measurement +
                ", date=" + date +
                '}';
    }
}
