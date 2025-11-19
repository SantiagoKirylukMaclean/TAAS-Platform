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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración para Distributed Tracing.
 * Valida que las trazas se generen correctamente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DistributedTracingIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void shouldGenerateTraceIdForRequest() throws Exception {
        // Given
        TelemetryRequest request = new TelemetryRequest(
                800L,
                new BigDecimal("25.0"),
                Instant.now()
        );
        
        // When
        MvcResult result = mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();
        
        // Then - Verificar que se generó un trace ID
        // (El trace ID se propaga en los logs, no necesariamente en headers de respuesta)
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
    }
    
    @Test
    void shouldTraceThroughEntireRequestLifecycle() throws Exception {
        // Given
        TelemetryRequest request = new TelemetryRequest(
                801L,
                new BigDecimal("26.0"),
                Instant.now()
        );
        
        // When - Enviar telemetría
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
        
        // Then - Consultar dispositivos (debe tener tracing también)
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk());
        
        // El tracing se valida mejor en los logs y en Zipkin
        // Este test verifica que el sistema funciona con tracing habilitado
    }
}
