package com.koni.telemetry.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for persisting fallback events when Kafka is unavailable.
 * This entity stores events that failed to publish to Kafka due to circuit breaker
 * being open, allowing them to be replayed later when Kafka becomes available.
 */
@Entity
@Table(
    name = "fallback_events",
    indexes = {
        @Index(
            name = "idx_fallback_events_failed_at",
            columnList = "failed_at DESC"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FallbackEventEntity {
    
    @Id
    @Column(name = "event_id")
    private UUID eventId;
    
    @Column(name = "device_id", nullable = false)
    private Long deviceId;
    
    @Column(name = "measurement", nullable = false, precision = 10, scale = 2)
    private BigDecimal measurement;
    
    @Column(name = "date", nullable = false)
    private Instant date;
    
    @Column(name = "failed_at", nullable = false, updatable = false)
    private Instant failedAt;
    
    /**
     * Constructor for creating a new FallbackEventEntity without failedAt.
     * The failedAt timestamp will be set automatically.
     *
     * @param eventId the unique event identifier
     * @param deviceId the device identifier
     * @param measurement the temperature measurement
     * @param date the timestamp of the measurement
     */
    public FallbackEventEntity(UUID eventId, Long deviceId, BigDecimal measurement, Instant date) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.measurement = measurement;
        this.date = date;
    }
    
    @PrePersist
    protected void onCreate() {
        if (failedAt == null) {
            failedAt = Instant.now();
        }
    }
}
