package com.koni.telemetry.application.query;

import com.koni.telemetry.domain.model.DeviceProjection;
import com.koni.telemetry.domain.repository.DeviceProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetDevicesQueryHandler.
 * Tests retrieving all devices, empty results, and DTO mapping.
 * 
 */
@ExtendWith(MockitoExtension.class)
class GetDevicesQueryHandlerTest {
    
    @Mock
    private DeviceProjectionRepository repository;
    
    private GetDevicesQueryHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new GetDevicesQueryHandler(repository);
    }
    
    @Test
    void shouldRetrieveAllDevices() {
        // Given
        Instant now = Instant.now();
        DeviceProjection device1 = new DeviceProjection(
                1L,
                new BigDecimal("25.5"),
                now.minus(1, ChronoUnit.HOURS)
        );
        DeviceProjection device2 = new DeviceProjection(
                2L,
                new BigDecimal("30.2"),
                now.minus(30, ChronoUnit.MINUTES)
        );
        DeviceProjection device3 = new DeviceProjection(
                3L,
                new BigDecimal("18.7"),
                now.minus(15, ChronoUnit.MINUTES)
        );
        
        when(repository.findAll()).thenReturn(Arrays.asList(device1, device2, device3));
        
        GetDevicesQuery query = new GetDevicesQuery();
        
        // When
        List<DeviceResponse> result = handler.handle(query);
        
        // Then
        verify(repository).findAll();
        
        assertThat(result).hasSize(3);
        
        // Verify first device mapping
        DeviceResponse response1 = result.get(0);
        assertThat(response1.getDeviceId()).isEqualTo(1L);
        assertThat(response1.getMeasurement()).isEqualTo(new BigDecimal("25.5"));
        assertThat(response1.getDate()).isEqualTo(device1.getLatestDate());
        
        // Verify second device mapping
        DeviceResponse response2 = result.get(1);
        assertThat(response2.getDeviceId()).isEqualTo(2L);
        assertThat(response2.getMeasurement()).isEqualTo(new BigDecimal("30.2"));
        assertThat(response2.getDate()).isEqualTo(device2.getLatestDate());
        
        // Verify third device mapping
        DeviceResponse response3 = result.get(2);
        assertThat(response3.getDeviceId()).isEqualTo(3L);
        assertThat(response3.getMeasurement()).isEqualTo(new BigDecimal("18.7"));
        assertThat(response3.getDate()).isEqualTo(device3.getLatestDate());
    }
    
    @Test
    void shouldReturnEmptyListWhenNoDevicesExist() {
        // Given
        when(repository.findAll()).thenReturn(Collections.emptyList());
        
        GetDevicesQuery query = new GetDevicesQuery();
        
        // When
        List<DeviceResponse> result = handler.handle(query);
        
        // Then
        verify(repository).findAll();
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldCorrectlyMapDeviceProjectionToDeviceResponse() {
        // Given
        Long deviceId = 42L;
        BigDecimal measurement = new BigDecimal("22.3");
        Instant timestamp = Instant.parse("2025-01-31T10:30:00Z");
        
        DeviceProjection projection = new DeviceProjection(deviceId, measurement, timestamp);
        when(repository.findAll()).thenReturn(Collections.singletonList(projection));
        
        GetDevicesQuery query = new GetDevicesQuery();
        
        // When
        List<DeviceResponse> result = handler.handle(query);
        
        // Then
        assertThat(result).hasSize(1);
        
        DeviceResponse response = result.get(0);
        assertThat(response.getDeviceId()).isEqualTo(deviceId);
        assertThat(response.getMeasurement()).isEqualTo(measurement);
        assertThat(response.getDate()).isEqualTo(timestamp);
    }
}
