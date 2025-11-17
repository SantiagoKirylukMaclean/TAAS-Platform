package com.koni.telemetry.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for persisting device projection data in the database.
 * This entity represents the read model in the CQRS pattern.
 * It maintains the latest temperature measurement for each device.
 */
@Entity
@Table(
    name = "device_projection",
    indexes = {
        @Index(
            name = "idx_device_projection_date",
            columnList = "latest_date DESC"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceProjectionEntity {
    
    @Id
    @Column(name = "device_id")
    private Long deviceId;
    
    @Column(name = "latest_measurement", nullable = false, precision = 10, scale = 2)
    private BigDecimal latestMeasurement;
    
    @Column(name = "latest_date", nullable = false)
    private Instant latestDate;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    /**
     * Constructor for creating a new DeviceProjectionEntity without updatedAt.
     * The updatedAt timestamp will be set automatically.
     *
     * @param deviceId the device identifier
     * @param latestMeasurement the latest temperature measurement
     * @param latestDate the timestamp of the latest measurement
     */
    public DeviceProjectionEntity(Long deviceId, BigDecimal latestMeasurement, Instant latestDate) {
        this.deviceId = deviceId;
        this.latestMeasurement = latestMeasurement;
        this.latestDate = latestDate;
    }
    
    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
