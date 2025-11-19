package com.koni.telemetry.infrastructure.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.infrastructure.web.dto.TelemetryRequest;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for distributed tracing - Happy path only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class DistributedTracingIntegrationTest {

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
        // Configure tracing to use in-memory reporter for testing
        registry.add("management.tracing.sampling.probability", () -> "1.0");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Tracer tracer;
    
    // Pattern to extract trace ID from logs: [app-name,traceId,spanId]
    private static final Pattern TRACE_PATTERN = Pattern.compile("\\[telemetry-challenge,([a-f0-9]+),([a-f0-9]+)\\]");

    @Test
    void shouldGenerateTraceIdForRequest(CapturedOutput output) throws Exception {
        TelemetryRequest request = new TelemetryRequest(
                1L,
                new BigDecimal("25.5"),
                Instant.now().minus(1, ChronoUnit.HOURS)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        
        String logOutput = output.getOut();
        Matcher matcher = TRACE_PATTERN.matcher(logOutput);
        
        assertThat(matcher.find()).isTrue();
        
        String traceId = matcher.group(1);
        assertThat(traceId).matches("[a-f0-9]+").hasSizeGreaterThanOrEqualTo(16);
    }

    @Test
    void shouldTraceThroughEntireRequestLifecycle(CapturedOutput output) throws Exception {
        TelemetryRequest request = new TelemetryRequest(
                100L,
                new BigDecimal("28.7"),
                Instant.now().minus(1, ChronoUnit.HOURS)
        );

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        String logOutput = output.getOut();
        Matcher matcher = TRACE_PATTERN.matcher(logOutput);
        assertThat(matcher.find()).isTrue();
        
        String traceId = matcher.group(1);
        
        int occurrences = 0;
        Matcher allMatches = TRACE_PATTERN.matcher(logOutput);
        while (allMatches.find()) {
            if (allMatches.group(1).equals(traceId)) {
                occurrences++;
            }
        }
        
        assertThat(occurrences).isGreaterThanOrEqualTo(2);
    }
}
