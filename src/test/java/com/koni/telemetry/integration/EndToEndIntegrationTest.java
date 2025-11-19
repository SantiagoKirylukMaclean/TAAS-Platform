package com.koni.telemetry.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.application.query.DeviceResponse;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración End-to-End completo.
 * Valida el flujo completo: POST telemetría -> Kafka -> Consumer -> Proyección -> GET dispositivos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndIntegrationTest {
    
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
    void shouldProcessTelemetryEndToEnd() throws Exception {
        // Given
        Long deviceId = 100L;
        BigDecimal measurement = new BigDecimal("25.5");
        Instant timestamp = Instant.now();
        
        TelemetryRequest request = new TelemetryRequest(deviceId, measurement, timestamp);
        
        // When - Enviar telemetría via POST
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
        
        // Then - Verificar que se persistió en la base de datos
        await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    var telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(1);
                    assertThat(telemetries.get(0).getDeviceId()).isEqualTo(deviceId);
                });
        
        // Then - Verificar que el consumer procesó el evento y actualizó la proyección
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var projection = deviceProjectionRepository.findByDeviceId(deviceId);
                    assertThat(projection).isPresent();
                    assertThat(projection.get().getLatestMeasurement()).isEqualByComparingTo(measurement);
                });
        
        // Then - Verificar que la proyección es accesible via GET
        MvcResult result = mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        List<DeviceResponse> devices = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeviceResponse.class)
        );
        
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).getDeviceId()).isEqualTo(deviceId);
        assertThat(devices.get(0).getMeasurement()).isEqualByComparingTo(measurement);
    }
    
    @Test
    void shouldProcessMultipleTelemetriesForSameDevice() throws Exception {
        // Given - Tres telemetrías para el mismo dispositivo
        Long deviceId = 200L;
        
        TelemetryRequest request1 = new TelemetryRequest(
                deviceId,
                new BigDecimal("20.0"),
                Instant.parse("2025-01-31T10:00:00Z")
        );
        
        TelemetryRequest request2 = new TelemetryRequest(
                deviceId,
                new BigDecimal("22.5"),
                Instant.parse("2025-01-31T10:05:00Z")
        );
        
        TelemetryRequest request3 = new TelemetryRequest(
                deviceId,
                new BigDecimal("25.0"),
                Instant.parse("2025-01-31T10:10:00Z")
        );
        
        // When - Enviar las tres telemetrías
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isAccepted());
        
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isAccepted());
        
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request3)))
                .andExpect(status().isAccepted());
        
        // Then - Verificar que se persistieron las tres
        await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    var telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(3);
                });
        
        // Then - Verificar que la proyección muestra solo la última medición
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var projection = deviceProjectionRepository.findByDeviceId(deviceId);
                    assertThat(projection).isPresent();
                    assertThat(projection.get().getLatestMeasurement())
                            .isEqualByComparingTo(new BigDecimal("25.0"));
                    assertThat(projection.get().getLatestDate())
                            .isEqualTo(Instant.parse("2025-01-31T10:10:00Z"));
                });
    }
    
    @Test
    void shouldProcessTelemetriesForMultipleDevices() throws Exception {
        // Given - Telemetrías para tres dispositivos diferentes
        TelemetryRequest request1 = new TelemetryRequest(
                300L,
                new BigDecimal("18.5"),
                Instant.parse("2025-01-31T11:00:00Z")
        );
        
        TelemetryRequest request2 = new TelemetryRequest(
                301L,
                new BigDecimal("22.0"),
                Instant.parse("2025-01-31T11:05:00Z")
        );
        
        TelemetryRequest request3 = new TelemetryRequest(
                302L,
                new BigDecimal("26.5"),
                Instant.parse("2025-01-31T11:10:00Z")
        );
        
        // When - Enviar las tres telemetrías
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isAccepted());
        
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isAccepted());
        
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request3)))
                .andExpect(status().isAccepted());
        
        // Then - Verificar que se persistieron las tres
        await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    var telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(3);
                });
        
        // Then - Verificar que se crearon proyecciones para los tres dispositivos
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var projections = deviceProjectionRepository.findAll();
                    assertThat(projections).hasSize(3);
                });
        
        // Then - Verificar que todos los dispositivos son accesibles via GET
        MvcResult result = mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        List<DeviceResponse> devices = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeviceResponse.class)
        );
        
        assertThat(devices).hasSize(3);
    }
}
