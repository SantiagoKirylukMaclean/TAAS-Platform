package com.koni.telemetry.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.infrastructure.persistence.repository.DeviceProjectionJpaRepository;
import com.koni.telemetry.infrastructure.persistence.repository.TelemetryJpaRepository;
import com.koni.telemetry.infrastructure.web.dto.TelemetryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración para telemetría fuera de orden.
 * Valida que el sistema maneje correctamente eventos que llegan desordenados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutOfOrderTelemetryIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private TelemetryJpaRepository telemetryRepository;
    
    @Autowired
    private DeviceProjectionJpaRepository deviceProjectionRepository;
    
    @BeforeEach
    void setUp() {
        deviceProjectionRepository.deleteAll();
        telemetryRepository.deleteAll();
    }
    
    @Test
    void shouldHandleOutOfOrderTelemetry() throws Exception {
        // Given - Tres telemetrías: T1 (10:00), T2 (10:05), T3 (10:02)
        Long deviceId = 600L;
        
        TelemetryRequest t1 = new TelemetryRequest(
                deviceId,
                new BigDecimal("20.0"),
                Instant.parse("2025-01-31T10:00:00Z")
        );
        
        TelemetryRequest t2 = new TelemetryRequest(
                deviceId,
                new BigDecimal("22.5"),
                Instant.parse("2025-01-31T10:05:00Z")
        );
        
        TelemetryRequest t3 = new TelemetryRequest(
                deviceId,
                new BigDecimal("21.0"),
                Instant.parse("2025-01-31T10:02:00Z")
        );
        
        // When - Enviar T1 (10:00)
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1)))
                .andExpect(status().isAccepted());
        
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var projection = deviceProjectionRepository.findByDeviceId(deviceId);
                    assertThat(projection).isPresent();
                    assertThat(projection.get().getLatestDate())
                            .isEqualTo(Instant.parse("2025-01-31T10:00:00Z"));
                });
        
        // When - Enviar T2 (10:05)
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t2)))
                .andExpect(status().isAccepted());
        
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var projection = deviceProjectionRepository.findByDeviceId(deviceId);
                    assertThat(projection).isPresent();
                    assertThat(projection.get().getLatestDate())
                            .isEqualTo(Instant.parse("2025-01-31T10:05:00Z"));
                });
        
        // When - Enviar T3 (10:02) - fuera de orden
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t3)))
                .andExpect(status().isAccepted());
        
        // Then - Verificar que hay 3 registros
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(3);
                });
        
        // Then - Verificar que la proyección muestra T2 (el más reciente)
        var projection = deviceProjectionRepository.findByDeviceId(deviceId);
        assertThat(projection).isPresent();
        assertThat(projection.get().getLatestMeasurement())
                .isEqualByComparingTo(new BigDecimal("22.5"));
        assertThat(projection.get().getLatestDate())
                .isEqualTo(Instant.parse("2025-01-31T10:05:00Z"));
    }
}
