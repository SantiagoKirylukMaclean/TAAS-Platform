package com.koni.telemetry.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * TelemetryRecorded domain event.
 * This immutable event is published when telemetry data is successfully recorded.
 * It is used to propagate changes from the write side to the read side in the CQRS pattern.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class TelemetryRecorded {
    
    private final UUID eventId;
    private final Long deviceId;
    private final BigDecimal measurement;
    private final Instant date;
    private final Instant recordedAt;
    
    /**
     * Creates a new TelemetryRecorded event.
     * This constructor is used by Jackson for JSON deserialization.
     *
     * @param eventId the unique identifier of this event
     * @param deviceId the unique identifier of the device
     * @param measurement the temperature measurement value
     * @param date the timestamp when the measurement was taken
     * @param recordedAt the timestamp when the event was recorded
     */
    @JsonCreator
    public TelemetryRecorded(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("deviceId") Long deviceId,
            @JsonProperty("measurement") BigDecimal measurement,
            @JsonProperty("date") Instant date,
            @JsonProperty("recordedAt") Instant recordedAt) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.measurement = measurement;
        this.date = date;
        this.recordedAt = recordedAt;
    }
}
