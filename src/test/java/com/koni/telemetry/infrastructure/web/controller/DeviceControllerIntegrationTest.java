package com.koni.telemetry.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.application.query.DeviceResponse;
import com.koni.telemetry.infrastructure.persistence.entity.DeviceProjectionEntity;
import com.koni.telemetry.infrastructure.persistence.repository.DeviceProjectionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for DeviceController.
 * Uses TestContainers to spin up a real PostgreSQL instance.
 * 
 * Tests:
 * - GET /api/v1/devices with existing data
 * - GET /api/v1/devices with empty result
 * 
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DeviceControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16")
    )
            .withDatabaseName("telemetry_test")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
    }
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private DeviceProjectionJpaRepository deviceProjectionRepository;
    
    @BeforeEach
    void setUp() {
        // Clean up database before each test
        deviceProjectionRepository.deleteAll();
    }
    
    @Test
    void shouldReturnAllDevicesWithLatestMeasurements() throws Exception {
        // Given - Create test data in the device projection table
        DeviceProjectionEntity device1 = new DeviceProjectionEntity(
                1L,
                new BigDecimal("25.5"),
                Instant.parse("2025-01-31T10:00:00Z")
        );
        
        DeviceProjectionEntity device2 = new DeviceProjectionEntity(
                2L,
                new BigDecimal("30.0"),
                Instant.parse("2025-01-31T10:05:00Z")
        );
        
        DeviceProjectionEntity device3 = new DeviceProjectionEntity(
                3L,
                new BigDecimal("18.2"),
                Instant.parse("2025-01-31T10:10:00Z")
        );
        
        deviceProjectionRepository.saveAll(List.of(device1, device2, device3));
        
        // When - Call GET /api/v1/devices
        MvcResult result = mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Verify response contains all devices
        String responseBody = result.getResponse().getContentAsString();
        List<DeviceResponse> devices = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeviceResponse.class)
        );
        
        assertThat(devices).hasSize(3);
        
        // Verify device 1
        DeviceResponse deviceResponse1 = devices.stream()
                .filter(d -> d.getDeviceId().equals(1L))
                .findFirst()
                .orElseThrow();
        assertThat(deviceResponse1.getMeasurement()).isEqualByComparingTo(new BigDecimal("25.5"));
        assertThat(deviceResponse1.getDate()).isEqualTo(Instant.parse("2025-01-31T10:00:00Z"));
        
        // Verify device 2
        DeviceResponse deviceResponse2 = devices.stream()
                .filter(d -> d.getDeviceId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertThat(deviceResponse2.getMeasurement()).isEqualByComparingTo(new BigDecimal("30.0"));
        assertThat(deviceResponse2.getDate()).isEqualTo(Instant.parse("2025-01-31T10:05:00Z"));
        
        // Verify device 3
        DeviceResponse deviceResponse3 = devices.stream()
                .filter(d -> d.getDeviceId().equals(3L))
                .findFirst()
                .orElseThrow();
        assertThat(deviceResponse3.getMeasurement()).isEqualByComparingTo(new BigDecimal("18.2"));
        assertThat(deviceResponse3.getDate()).isEqualTo(Instant.parse("2025-01-31T10:10:00Z"));
    }
    
    @Test
    void shouldReturnEmptyListWhenNoDevicesExist() throws Exception {
        // Given - No devices in database (cleaned up in setUp)
        
        // When - Call GET /api/v1/devices
        MvcResult result = mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Verify response is an empty list
        String responseBody = result.getResponse().getContentAsString();
        List<DeviceResponse> devices = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeviceResponse.class)
        );
        
        assertThat(devices).isEmpty();
    }
    
    @Test
    void shouldReturnSingleDeviceWhenOnlyOneExists() throws Exception {
        // Given - Create a single device
        DeviceProjectionEntity device = new DeviceProjectionEntity(
                42L,
                new BigDecimal("22.7"),
                Instant.parse("2025-01-31T12:30:00Z")
        );
        
        deviceProjectionRepository.save(device);
        
        // When - Call GET /api/v1/devices
        MvcResult result = mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andReturn();
        
        // Then - Verify response contains exactly one device
        String responseBody = result.getResponse().getContentAsString();
        List<DeviceResponse> devices = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeviceResponse.class)
        );
        
        assertThat(devices).hasSize(1);
        
        DeviceResponse deviceResponse = devices.get(0);
        assertThat(deviceResponse.getDeviceId()).isEqualTo(42L);
        assertThat(deviceResponse.getMeasurement()).isEqualByComparingTo(new BigDecimal("22.7"));
        assertThat(deviceResponse.getDate()).isEqualTo(Instant.parse("2025-01-31T12:30:00Z"));
    }
}
