package com.koni.telemetry.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DeviceProjection entity representing the latest temperature measurement for a device.
 * This is a mutable entity used in the read model (query side) of the CQRS pattern.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceProjection {
    
    private Long deviceId;
    private BigDecimal latestMeasurement;
    private Instant latestDate;
    
    /**
     * Creates a new DeviceProjection for a device with no measurements yet.
     *
     * @param deviceId the unique identifier of the device
     */
    public DeviceProjection(Long deviceId) {
        this.deviceId = deviceId;
    }
    
    /**
     * Checks if the current projection has a newer timestamp than the given date.
     * Used for out-of-order event detection.
     *
     * @param eventDate the timestamp to compare against
     * @return true if the current latest date is after the event date, false otherwise
     */
    public boolean isNewerThan(Instant eventDate) {
        return latestDate != null && latestDate.isAfter(eventDate);
    }
    
    /**
     * Updates the projection with new measurement data.
     * This method should only be called when the new data is confirmed to be newer.
     *
     * @param measurement the new temperature measurement
     * @param date the timestamp of the new measurement
     */
    public void updateWith(BigDecimal measurement, Instant date) {
        this.latestMeasurement = measurement;
        this.latestDate = date;
    }
    
    @Override
    public String toString() {
        return "DeviceProjection{" +
                "deviceId=" + deviceId +
                ", latestMeasurement=" + latestMeasurement +
                ", latestDate=" + latestDate +
                '}';
    }
}
