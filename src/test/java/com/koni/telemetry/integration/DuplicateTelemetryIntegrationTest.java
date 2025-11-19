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
 * Test de integración para telemetría duplicada.
 * Valida que el sistema maneje duplicados de forma idempotente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DuplicateTelemetryIntegrationTest {
    
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
    void shouldHandleDuplicateTelemetryIdempotently() throws Exception {
        // Given - Misma telemetría enviada dos veces
        Long deviceId = 500L;
        BigDecimal measurement = new BigDecimal("28.5");
        Instant timestamp = Instant.parse("2025-01-31T14:30:00Z");
        
        TelemetryRequest request = new TelemetryRequest(deviceId, measurement, timestamp);
        String requestJson = objectMapper.writeValueAsString(request);
        
        // When - Enviar primera vez
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted());
        
        // Esperar a que se procese
        await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    assertThat(telemetryRepository.findAll()).hasSize(1);
                });
        
        // When - Enviar segunda vez (duplicado)
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted());
        
        // Then - Verificar que solo hay UN registro
        await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    var telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(1);
                    assertThat(telemetries.get(0).getDeviceId()).isEqualTo(deviceId);
                    assertThat(telemetries.get(0).getMeasurement()).isEqualByComparingTo(measurement);
                });
        
        // Verificar idempotencia
        boolean exists = telemetryRepository.existsByDeviceIdAndDate(deviceId, timestamp);
        assertThat(exists).isTrue();
    }
}
