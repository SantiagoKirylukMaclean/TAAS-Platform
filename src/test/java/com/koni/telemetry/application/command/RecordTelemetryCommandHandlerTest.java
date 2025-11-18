package com.koni.telemetry.application.command;

import com.koni.telemetry.application.port.EventPublisher;
import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.domain.exception.ValidationException;
import com.koni.telemetry.domain.model.Telemetry;
import com.koni.telemetry.domain.repository.TelemetryRepository;
import com.koni.telemetry.infrastructure.observability.TelemetryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecordTelemetryCommandHandler.
 * Tests validation, idempotency, persistence, and event publishing.
 */
@ExtendWith(MockitoExtension.class)
class RecordTelemetryCommandHandlerTest {
    
    @Mock
    private TelemetryRepository telemetryRepository;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @Mock
    private TelemetryMetrics telemetryMetrics;
    
    private RecordTelemetryCommandHandler handler;
    
    @BeforeEach
    void setUp() {
        // Mock the recordProcessingTime to execute the operation immediately
        doAnswer(invocation -> {
            Runnable operation = invocation.getArgument(0);
            operation.run();
            return null;
        }).when(telemetryMetrics).recordProcessingTime(any(Runnable.class));
        
        handler = new RecordTelemetryCommandHandler(telemetryRepository, eventPublisher, telemetryMetrics);
    }
    
    @Test
    void shouldSuccessfullyRecordValidTelemetry() {
        // Given
        Long deviceId = 1L;
        BigDecimal measurement = new BigDecimal("25.5");
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        RecordTelemetryCommand command = new RecordTelemetryCommand(deviceId, measurement, date);
        
        when(telemetryRepository.exists(deviceId, date)).thenReturn(false);
        
        // When
        handler.handle(command);
        
        // Then
        ArgumentCaptor<Telemetry> telemetryCaptor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryRepository).save(telemetryCaptor.capture());
        
        Telemetry savedTelemetry = telemetryCaptor.getValue();
        assertThat(savedTelemetry.getDeviceId()).isEqualTo(deviceId);
        assertThat(savedTelemetry.getMeasurement()).isEqualTo(measurement);
        assertThat(savedTelemetry.getDate()).isEqualTo(date);
        
        ArgumentCaptor<TelemetryRecorded> eventCaptor = ArgumentCaptor.forClass(TelemetryRecorded.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        
        TelemetryRecorded publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getDeviceId()).isEqualTo(deviceId);
        assertThat(publishedEvent.getMeasurement()).isEqualTo(measurement);
        assertThat(publishedEvent.getDate()).isEqualTo(date);
        assertThat(publishedEvent.getEventId()).isNotNull();
        assertThat(publishedEvent.getRecordedAt()).isNotNull();
        
        // Verify metrics were recorded
        verify(telemetryMetrics).recordTelemetryReceived();
        verify(telemetryMetrics).recordProcessingTime(any(Runnable.class));
    }
    
    @Test
    void shouldRejectTelemetryWithNullDeviceId() {
        // Given
        RecordTelemetryCommand command = new RecordTelemetryCommand(
                null, 
                new BigDecimal("25.5"), 
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        
        // When/Then
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("deviceId is required");
        
        verify(telemetryRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
    
    @Test
    void shouldRejectTelemetryWithNullMeasurement() {
        // Given
        RecordTelemetryCommand command = new RecordTelemetryCommand(
                1L, 
                null, 
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        
        // When/Then
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("measurement is required");
        
        verify(telemetryRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
    
    @Test
    void shouldRejectTelemetryWithFutureDate() {
        // Given
        RecordTelemetryCommand command = new RecordTelemetryCommand(
                1L, 
                new BigDecimal("25.5"), 
                Instant.now().plus(1, ChronoUnit.HOURS)
        );
        
        // When/Then
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("date cannot be in the future");
        
        verify(telemetryRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
    
    @Test
    void shouldHandleDuplicateTelemetryIdempotently() {
        // Given
        Long deviceId = 1L;
        BigDecimal measurement = new BigDecimal("25.5");
        Instant date = Instant.now().minus(1, ChronoUnit.HOURS);
        RecordTelemetryCommand command = new RecordTelemetryCommand(deviceId, measurement, date);
        
        when(telemetryRepository.exists(deviceId, date)).thenReturn(true);
        
        // When
        handler.handle(command);
        
        // Then
        verify(telemetryRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
        
        // Verify duplicate metric was recorded
        verify(telemetryMetrics).recordDuplicate();
        verify(telemetryMetrics).recordTelemetryReceived();
    }
}
