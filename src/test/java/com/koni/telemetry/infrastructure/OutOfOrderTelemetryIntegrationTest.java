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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for out-of-order telemetry edge case.
 * 
 * This test validates the out-of-order handling requirement:
 * - Send T1 (timestamp 10:00)
 * - Send T2 (timestamp 10:05)
 * - Send T3 (timestamp 10:02) - out of order
 * - Verify all three records in telemetry table
 * - Verify projection shows T2 (latest timestamp)
 * - Verify T3 logged as out-of-order
 * 
 */
@org.junit.jupiter.api.Disabled("Testcontainers stability issue - run manually")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class OutOfOrderTelemetryIntegrationTest {
    
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
    void shouldHandleOutOfOrderTelemetry(CapturedOutput output) throws Exception {
        // Given - Three telemetries with timestamps: T1 (10:00), T2 (10:05), T3 (10:02)
        Long deviceId = 600L;
        
        // T1 - First telemetry at 10:00
        TelemetryRequest t1 = new TelemetryRequest(
                deviceId,
                new BigDecimal("20.0"),
                Instant.parse("2025-01-31T10:00:00Z")
        );
        
        // T2 - Second telemetry at 10:05 (newer)
        TelemetryRequest t2 = new TelemetryRequest(
                deviceId,
                new BigDecimal("22.5"),
                Instant.parse("2025-01-31T10:05:00Z")
        );
        
        // T3 - Third telemetry at 10:02 (out of order - older than T2)
        TelemetryRequest t3 = new TelemetryRequest(
                deviceId,
                new BigDecimal("21.0"),
                Instant.parse("2025-01-31T10:02:00Z")
        );
        
        // When - Send T1 (timestamp 10:00)
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1)))
                .andExpect(status().isAccepted());
        
        // Wait for T1 to be processed
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Optional<DeviceProjectionEntity> projection = 
                            deviceProjectionRepository.findByDeviceId(deviceId);
                    assertThat(projection).isPresent();
                    assertThat(projection.get().getLatestDate())
                            .isEqualTo(Instant.parse("2025-01-31T10:00:00Z"));
                });
        
        // When - Send T2 (timestamp 10:05)
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t2)))
                .andExpect(status().isAccepted());
        
        // Wait for T2 to be processed and projection updated
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Optional<DeviceProjectionEntity> projection = 
                            deviceProjectionRepository.findByDeviceId(deviceId);
                    assertThat(projection).isPresent();
                    assertThat(projection.get().getLatestDate())
                            .isEqualTo(Instant.parse("2025-01-31T10:05:00Z"));
                });
        
        // When - Send T3 (timestamp 10:02) - out of order
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t3)))
                .andExpect(status().isAccepted());
        
        // Wait for T3 to be persisted and consumer to process
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    List<TelemetryEntity> telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(3);
                });
        
        // Then - Verify all three records in telemetry table (Requirement 6.1)
        List<TelemetryEntity> telemetries = telemetryRepository.findAll();
        assertThat(telemetries).hasSize(3);
        
        // Verify each telemetry is persisted
        assertThat(telemetries)
                .extracting(TelemetryEntity::getDate)
                .containsExactlyInAnyOrder(
                        Instant.parse("2025-01-31T10:00:00Z"),
                        Instant.parse("2025-01-31T10:05:00Z"),
                        Instant.parse("2025-01-31T10:02:00Z")
                );
        
        // Verify measurements using compareTo for BigDecimal (scale-independent comparison)
        assertThat(telemetries)
                .extracting(TelemetryEntity::getMeasurement)
                .satisfies(measurements -> {
                    assertThat(measurements).hasSize(3);
                    assertThat(measurements).anySatisfy(m -> 
                            assertThat(m).isEqualByComparingTo(new BigDecimal("20.0")));
                    assertThat(measurements).anySatisfy(m -> 
                            assertThat(m).isEqualByComparingTo(new BigDecimal("22.5")));
                    assertThat(measurements).anySatisfy(m -> 
                            assertThat(m).isEqualByComparingTo(new BigDecimal("21.0")));
                });
        
        // Then - Verify projection shows T2 (latest timestamp) (Requirement 6.2, 6.5)
        Optional<DeviceProjectionEntity> projection = 
                deviceProjectionRepository.findByDeviceId(deviceId);
        assertThat(projection).isPresent();
        assertThat(projection.get().getLatestMeasurement())
                .isEqualByComparingTo(new BigDecimal("22.5")); // T2's measurement
        assertThat(projection.get().getLatestDate())
                .isEqualTo(Instant.parse("2025-01-31T10:05:00Z")); // T2's timestamp
        
        // Then - Verify T3 logged as out-of-order (Requirement 6.4)
        String logOutput = output.getOut();
        assertThat(logOutput)
                .contains("Out-of-order telemetry")
                .contains("deviceId=" + deviceId)
                .contains("2025-01-31T10:02:00Z"); // T3's timestamp
    }
}
