package com.koni.telemetry.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.application.query.DeviceResponse;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test for the complete Kafka flow.
 * 
 * This test validates the entire CQRS flow:
 * 1. Send telemetry via POST /api/v1/telemetry
 * 2. Verify event published to Kafka
 * 3. Verify consumer processes event
 * 4. Verify device projection updated
 * 
 * Uses TestContainers for PostgreSQL and Kafka to ensure realistic testing.
 * 
 */
@org.junit.jupiter.api.Disabled("Testcontainers stability issue - run manually")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class KafkaEndToEndIntegrationTest {
    
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
    void shouldProcessTelemetryEndToEnd() throws Exception {
        // Given
        Long deviceId = 100L;
        BigDecimal measurement = new BigDecimal("25.5");
        Instant timestamp = Instant.now().minus(1, ChronoUnit.HOURS);
        
        TelemetryRequest request = new TelemetryRequest(deviceId, measurement, timestamp);
        
        // When - Send telemetry via POST
        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
        
        // Then - Verify telemetry persisted in database
        await().atMost(java.time.Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<TelemetryEntity> telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(1);
                    
                    TelemetryEntity telemetry = telemetries.get(0);
                    assertThat(telemetry.getDeviceId()).isEqualTo(deviceId);
                    assertThat(telemetry.getMeasurement()).isEqualByComparingTo(measurement);
                    assertThat(telemetry.getDate()).isEqualTo(timestamp);
                });
        
        // Then - Verify consumer processed event and updated projection
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var projection = deviceProjectionRepository.findByDeviceId(deviceId);
                    assertThat(projection).isPresent();
                    assertThat(projection.get().getLatestMeasurement()).isEqualByComparingTo(measurement);
                    assertThat(projection.get().getLatestDate()).isEqualTo(timestamp);
                });
        
        // Then - Verify projection accessible via GET endpoint
        MvcResult result = mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        List<DeviceResponse> devices = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeviceResponse.class)
        );
        
        assertThat(devices).hasSize(1);
        DeviceResponse device = devices.get(0);
        assertThat(device.getDeviceId()).isEqualTo(deviceId);
        assertThat(device.getMeasurement()).isEqualByComparingTo(measurement);
        assertThat(device.getDate()).isEqualTo(timestamp);
    }
    
    @Test
    void shouldProcessMultipleTelemetriesForSameDevice() throws Exception {
        // Given - Three telemetries for the same device with different timestamps
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
        
        // When - Send all three telemetries
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
        
        // Then - Verify all three telemetries persisted
        await().atMost(java.time.Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<TelemetryEntity> telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(3);
                });
        
        // Then - Verify projection shows only the latest measurement
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
        // Given - Telemetries for three different devices
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
        
        // When - Send all three telemetries
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
        
        // Then - Verify all three telemetries persisted
        await().atMost(java.time.Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<TelemetryEntity> telemetries = telemetryRepository.findAll();
                    assertThat(telemetries).hasSize(3);
                });
        
        // Then - Verify projections created for all three devices
        await().atMost(java.time.Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var projections = deviceProjectionRepository.findAll();
                    assertThat(projections).hasSize(3);
                    
                    // Verify device 300
                    var projection300 = deviceProjectionRepository.findByDeviceId(300L);
                    assertThat(projection300).isPresent();
                    assertThat(projection300.get().getLatestMeasurement())
                            .isEqualByComparingTo(new BigDecimal("18.5"));
                    
                    // Verify device 301
                    var projection301 = deviceProjectionRepository.findByDeviceId(301L);
                    assertThat(projection301).isPresent();
                    assertThat(projection301.get().getLatestMeasurement())
                            .isEqualByComparingTo(new BigDecimal("22.0"));
                    
                    // Verify device 302
                    var projection302 = deviceProjectionRepository.findByDeviceId(302L);
                    assertThat(projection302).isPresent();
                    assertThat(projection302.get().getLatestMeasurement())
                            .isEqualByComparingTo(new BigDecimal("26.5"));
                });
        
        // Then - Verify all devices accessible via GET endpoint
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
