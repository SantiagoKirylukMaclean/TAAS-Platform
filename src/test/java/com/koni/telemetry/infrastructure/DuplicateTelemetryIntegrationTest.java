package com.koni.telemetry.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.infrastructure.persistence.entity.DeviceProjectionEntity;
import com.koni.telemetry.infrastructure.persistence.entity.TelemetryEntity;
import com.koni.telemetry.infrastructure.persistence.repository.DeviceProjectionJpaRepository;
import com.koni.telemetry.infrastructure.persistence.repository.TelemetryJpaRepository;
import com.koni.telemetry.infrastructure.web.dto.TelemetryRequest;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for duplicate telemetry edge case.
 * 
 * This test validates the idempotency requirement:
 * - Send same telemetry twice
 * - Verify only one record in telemetry table
 * - Verify both requests return success
 * - Verify projection updated only once
 * 
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DuplicateTelemetryIntegrationTest {
    
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
    }
    
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
        // Clean up database before each test
        deviceProjectionRepository.deleteAll();
        telemetryRepository.deleteAll();
    }
    
    @Test
    void shouldHandleDuplicateTelemetryIdempotently() throws Exception {
        // Given - Same telemetry data to be sent twice
        Long deviceId = 500L;
        BigDecimal measurement = new BigDecimal("28.5");
        Instant timestamp = Instant.parse("2025-01-31T14:30:00Z");
        
        TelemetryRequest request = new TelemetryRequest(deviceId, measurement, timestamp);
        String requestJson = objectMapper.writeValueAsString(request);
        
        // When - Send telemetry first time
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted()); // Requirement 5.4: Return success
        
        // Wait for first telemetry to be processed
        await().atMost(java.time.Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    List<TelemetryEntity> telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(1);
                });
        
        // Wait for projection to be updated
        await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Optional<DeviceProjectionEntity> projection = 
                            deviceProjectionRepository.findByDeviceId(deviceId);
                    assertThat(projection).isPresent();
                });
        
        // When - Send same telemetry second time (duplicate)
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted()); // Requirement 5.4: Return success for duplicate
        
        // Give some time for potential duplicate processing
        Thread.sleep(2000);
        
        // Then - Verify only ONE record in telemetry table (Requirement 5.2)
        List<TelemetryEntity> telemetries = telemetryRepository.findAll();
        assertThat(telemetries)
                .hasSize(1)
                .satisfies(list -> {
                    TelemetryEntity telemetry = list.get(0);
                    assertThat(telemetry.getDeviceId()).isEqualTo(deviceId);
                    assertThat(telemetry.getMeasurement()).isEqualByComparingTo(measurement);
                    assertThat(telemetry.getDate()).isEqualTo(timestamp);
                });
        
        // Then - Verify projection updated only once (Requirement 5.3, 5.5)
        Optional<DeviceProjectionEntity> projection = 
                deviceProjectionRepository.findByDeviceId(deviceId);
        assertThat(projection).isPresent();
        assertThat(projection.get().getLatestMeasurement()).isEqualByComparingTo(measurement);
        assertThat(projection.get().getLatestDate()).isEqualTo(timestamp);
        
        // Verify idempotency key works correctly (Requirement 5.5)
        boolean exists = telemetryRepository.existsByDeviceIdAndDate(deviceId, timestamp);
        assertThat(exists).isTrue();
    }
}
