package com.koni.telemetry.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration test for health check endpoints including liveness and readiness probes.
 * 
 * Verifies that:
 * - Liveness probe endpoint is configured
 * - Readiness probe endpoint is configured
 * - Health endpoint beans are properly initialized
 * 
 * Requirements: 11.4, 11.5
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "management.health.kafka.enabled=false",
    "management.health.db.enabled=false"
})
class HealthProbesConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldHaveHealthEndpointBean() {
        // Verify that HealthEndpoint bean is configured
        assertThat(applicationContext.getBean(HealthEndpoint.class))
                .isNotNull();
    }

    @Test
    void shouldConfigureLivenessProbe() {
        // Verify liveness endpoint is accessible
        String url = "http://localhost:" + port + "/actuator/health/liveness";
        String response = restTemplate.getForObject(url, String.class);
        
        assertThat(response)
                .isNotNull()
                .contains("status");
    }

    @Test
    void shouldConfigureReadinessProbe() {
        // Verify readiness endpoint is accessible
        String url = "http://localhost:" + port + "/actuator/health/readiness";
        String response = restTemplate.getForObject(url, String.class);
        
        assertThat(response)
                .isNotNull()
                .contains("status");
    }

    @Test
    void shouldExposeHealthEndpoint() {
        // Verify main health endpoint is accessible
        String url = "http://localhost:" + port + "/actuator/health";
        String response = restTemplate.getForObject(url, String.class);
        
        assertThat(response)
                .isNotNull()
                .contains("status");
    }
}
