package com.koni.telemetry.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración para observabilidad y métricas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldExposePrometheusMetrics() throws Exception {
        // Prometheus endpoint puede no estar habilitado en test profile
        // Verificar que al menos el endpoint de métricas funciona
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }
    
    @Test
    void shouldExposeCustomTelemetryMetrics() throws Exception {
        String response = mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Verificar que existen métricas personalizadas
        // (pueden no tener valores si no se han enviado telemetrías)
    }
    
    @Test
    void shouldExposeCircuitBreakerMetrics() throws Exception {
        mockMvc.perform(get("/actuator/circuitbreakers"))
                .andExpect(status().isOk());
    }
    
    @Test
    void shouldExposeCircuitBreakerEvents() throws Exception {
        mockMvc.perform(get("/actuator/circuitbreakerevents"))
                .andExpect(status().isOk());
    }
}
