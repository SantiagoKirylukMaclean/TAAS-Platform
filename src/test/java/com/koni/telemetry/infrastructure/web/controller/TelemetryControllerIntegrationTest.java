package com.koni.telemetry.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.infrastructure.web.dto.TelemetryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TelemetryController.
 * Uses TestContainers to spin up real PostgreSQL and Kafka instances.
 * 
 * Tests:
 * - POST /api/v1/telemetry with valid data (202 Accepted)
 * - POST with invalid data (400 Bad Request)
 * - POST with duplicate data (idempotent behavior)
 */
@org.junit.jupiter.api.Disabled("Testcontainers stability issue - run manually")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TelemetryControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16")
    )
            .withDatabaseName("telemetry_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
        
        // Aggressive timeouts for tests
        registry.add("spring.kafka.consumer.properties.max.poll.interval.ms", () -> "10000");
        registry.add("spring.kafka.consumer.properties.session.timeout.ms", () -> "6000");
        registry.add("spring.kafka.consumer.properties.heartbeat.interval.ms", () -> "2000");
        registry.add("spring.kafka.consumer.properties.request.timeout.ms", () -> "5000");
        registry.add("spring.kafka.producer.properties.request.timeout.ms", () -> "5000");
        registry.add("spring.kafka.producer.properties.delivery.timeout.ms", () -> "10000");
    }
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void shouldAcceptValidTelemetryData() throws Exception {
        // Given
        TelemetryRequest request = new TelemetryRequest(
                1L,
                new BigDecimal("25.5"),
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        
        // When/Then
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }
    
    @Test
    void shouldRejectTelemetryWithNullDeviceId() throws Exception {
        // Given
        TelemetryRequest request = new TelemetryRequest(
                null,
                new BigDecimal("25.5"),
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        
        // When/Then
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldRejectTelemetryWithNullMeasurement() throws Exception {
        // Given
        TelemetryRequest request = new TelemetryRequest(
                1L,
                null,
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        
        // When/Then
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldRejectTelemetryWithNullDate() throws Exception {
        // Given
        TelemetryRequest request = new TelemetryRequest(
                1L,
                new BigDecimal("25.5"),
                null
        );
        
        // When/Then
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldHandleDuplicateTelemetryIdempotently() throws Exception {
        // Given
        TelemetryRequest request = new TelemetryRequest(
                2L,
                new BigDecimal("30.0"),
                Instant.parse("2025-01-31T10:00:00Z")
        );
        
        // When - Submit first time
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
        
        // Then - Submit second time (duplicate)
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }
}
