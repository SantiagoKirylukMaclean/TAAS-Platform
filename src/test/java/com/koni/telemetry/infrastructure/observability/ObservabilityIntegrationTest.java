package com.koni.telemetry.infrastructure.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.infrastructure.web.dto.TelemetryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for observability endpoints - Happy path only.
 */
@org.junit.jupiter.api.Disabled("Testcontainers stability issue - run manually")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "management.endpoints.web.exposure.include=health,metrics,prometheus,circuitbreakers,circuitbreakerevents",
        "management.endpoint.health.show-details=always",
        "management.metrics.export.prometheus.enabled=true"
    }
)
@Testcontainers
class ObservabilityIntegrationTest {

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
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldExposeCustomTelemetryMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("telemetry.received.total");
    }

    @Test
    void shouldIncrementMetricsAfterSendingRequests() {
        ResponseEntity<String> initialResponse = restTemplate.getForEntity(
                "/actuator/metrics/telemetry.received.total", String.class);
        assertThat(initialResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        double initialCount = extractMetricValue(initialResponse.getBody());

        for (int i = 0; i < 3; i++) {
            TelemetryRequest request = new TelemetryRequest(
                    (long) i,
                    new BigDecimal("25.5"),
                    Instant.now().minus(i, ChronoUnit.HOURS)
            );

            ResponseEntity<Void> postResponse = restTemplate.postForEntity(
                    "/api/v1/telemetry", request, Void.class);
            assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        ResponseEntity<String> finalResponse = restTemplate.getForEntity(
                "/actuator/metrics/telemetry.received.total", String.class);
        assertThat(finalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        double finalCount = extractMetricValue(finalResponse.getBody());
        assertThat(finalCount).isGreaterThanOrEqualTo(initialCount + 3);
    }

    private double extractMetricValue(String jsonResponse) {
        try {
            var jsonNode = objectMapper.readTree(jsonResponse);
            var measurements = jsonNode.get("measurements");
            if (measurements != null && measurements.isArray() && measurements.size() > 0) {
                return measurements.get(0).get("value").asDouble();
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
