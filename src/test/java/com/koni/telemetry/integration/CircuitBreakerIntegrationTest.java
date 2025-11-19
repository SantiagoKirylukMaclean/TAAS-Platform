package com.koni.telemetry.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.infrastructure.web.dto.TelemetryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración para Circuit Breaker.
 * Valida que el circuit breaker esté configurado y exponga métricas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CircuitBreakerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void shouldExposeCircuitBreakerMetrics() throws Exception {
        mockMvc.perform(get("/actuator/circuitbreakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakers").exists());
    }
    
    @Test
    void shouldExposeCircuitBreakerEvents() throws Exception {
        mockMvc.perform(get("/actuator/circuitbreakerevents"))
                .andExpect(status().isOk());
    }
    
    @Test
    void shouldAcceptTelemetryWhenKafkaIsAvailable() throws Exception {
        // Given - Kafka está disponible
        TelemetryRequest request = new TelemetryRequest(
                700L,
                new BigDecimal("30.0"),
                Instant.now()
        );
        
        // When/Then - Debe aceptar la telemetría
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }
}
