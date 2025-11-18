package com.koni.telemetry.application.consumer;

import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.domain.model.DeviceProjection;
import com.koni.telemetry.domain.repository.DeviceProjectionRepository;
import com.koni.telemetry.infrastructure.observability.TelemetryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TelemetryEventConsumer.
 * Tests projection updates, out-of-order event handling, and new device creation.
 * 
 */
@ExtendWith(MockitoExtension.class)
class TelemetryEventConsumerTest {
    
    @Mock
    private DeviceProjectionRepository deviceProjectionRepository;
    
    @Mock
    private Acknowledgment acknowledgment;
    
    @Mock
    private TelemetryMetrics telemetryMetrics;
    
    private TelemetryEventConsumer consumer;
    
    @BeforeEach
    void setUp() {
        // Mock the recordProcessingTime to execute the operation immediately
        doAnswer(invocation -> {
            Runnable operation = invocation.getArgument(0);
            operation.run();
            return null;
        }).when(telemetryMetrics).recordProcessingTime(any(Runnable.class));
        
        consumer = new TelemetryEventConsumer(deviceProjectionRepository, telemetryMetrics);
    }
    
    @Test
    void shouldUpdateProjectionWithNewerEvent() {
        // Given: Existing projection with older timestamp
        Long deviceId = 1L;
        Instant oldDate = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant newDate = Instant.now().minus(1, ChronoUnit.HOURS);
        
        DeviceProjection existingProjection = new DeviceProjection(
                deviceId,
                new BigDecimal("20.0"),
                oldDate
        );
        
        when(deviceProjectionRepository.findByDeviceId(deviceId))
                .thenReturn(Optional.of(existingProjection));
        
        TelemetryRecorded event = new TelemetryRecorded(
                UUID.randomUUID(),
                deviceId,
                new BigDecimal("25.5"),
                newDate,
                Instant.now()
        );
        
        // When
        consumer.consume(event, acknowledgment);
        
        // Then: Projection should be updated with newer data
        ArgumentCaptor<DeviceProjection> projectionCaptor = ArgumentCaptor.forClass(DeviceProjection.class);
        verify(deviceProjectionRepository).save(projectionCaptor.capture());
        
        DeviceProjection savedProjection = projectionCaptor.getValue();
        assertThat(savedProjection.getDeviceId()).isEqualTo(deviceId);
        assertThat(savedProjection.getLatestMeasurement()).isEqualTo(new BigDecimal("25.5"));
        assertThat(savedProjection.getLatestDate()).isEqualTo(newDate);
        
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    void shouldNotUpdateProjectionWithOutOfOrderEvent() {
        // Given: Existing projection with newer timestamp
        Long deviceId = 1L;
        Instant newerDate = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant olderDate = Instant.now().minus(2, ChronoUnit.HOURS);
        
        DeviceProjection existingProjection = new DeviceProjection(
                deviceId,
                new BigDecimal("25.5"),
                newerDate
        );
        
        when(deviceProjectionRepository.findByDeviceId(deviceId))
                .thenReturn(Optional.of(existingProjection));
        
        TelemetryRecorded outOfOrderEvent = new TelemetryRecorded(
                UUID.randomUUID(),
                deviceId,
                new BigDecimal("20.0"),
                olderDate,
                Instant.now()
        );
        
        // When
        consumer.consume(outOfOrderEvent, acknowledgment);
        
        // Then: Projection should NOT be updated
        verify(deviceProjectionRepository, never()).save(any());
        
        // But offset should still be acknowledged
        verify(acknowledgment).acknowledge();
        
        // Verify out-of-order metric was recorded
        verify(telemetryMetrics).recordOutOfOrder();
    }
    
    @Test
    void shouldCreateNewProjectionForNewDevice() {
        // Given: No existing projection for device
        Long deviceId = 1L;
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        
        when(deviceProjectionRepository.findByDeviceId(deviceId))
                .thenReturn(Optional.empty());
        
        TelemetryRecorded event = new TelemetryRecorded(
                UUID.randomUUID(),
                deviceId,
                new BigDecimal("25.5"),
                date,
                Instant.now()
        );
        
        // When
        consumer.consume(event, acknowledgment);
        
        // Then: New projection should be created and saved
        ArgumentCaptor<DeviceProjection> projectionCaptor = ArgumentCaptor.forClass(DeviceProjection.class);
        verify(deviceProjectionRepository).save(projectionCaptor.capture());
        
        DeviceProjection savedProjection = projectionCaptor.getValue();
        assertThat(savedProjection.getDeviceId()).isEqualTo(deviceId);
        assertThat(savedProjection.getLatestMeasurement()).isEqualTo(new BigDecimal("25.5"));
        assertThat(savedProjection.getLatestDate()).isEqualTo(date);
        
        verify(acknowledgment).acknowledge();
    }
    
    @Test
    void shouldUpdateProjectionWhenExistingHasNullDate() {
        // Given: Existing projection with null date (edge case)
        Long deviceId = 1L;
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        
        DeviceProjection existingProjection = new DeviceProjection(deviceId);
        
        when(deviceProjectionRepository.findByDeviceId(deviceId))
                .thenReturn(Optional.of(existingProjection));
        
        TelemetryRecorded event = new TelemetryRecorded(
                UUID.randomUUID(),
                deviceId,
                new BigDecimal("25.5"),
                date,
                Instant.now()
        );
        
        // When
        consumer.consume(event, acknowledgment);
        
        // Then: Projection should be updated (null date is not newer than any date)
        ArgumentCaptor<DeviceProjection> projectionCaptor = ArgumentCaptor.forClass(DeviceProjection.class);
        verify(deviceProjectionRepository).save(projectionCaptor.capture());
        
        DeviceProjection savedProjection = projectionCaptor.getValue();
        assertThat(savedProjection.getDeviceId()).isEqualTo(deviceId);
        assertThat(savedProjection.getLatestMeasurement()).isEqualTo(new BigDecimal("25.5"));
        assertThat(savedProjection.getLatestDate()).isEqualTo(date);
        
        verify(acknowledgment).acknowledge();
    }
}
