package com.koni.telemetry.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koni.telemetry.application.service.DlqManagementService;
import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.infrastructure.web.dto.DlqMessage;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Dead Letter Queue functionality - Happy path only.
 */
@org.junit.jupiter.api.Disabled("Testcontainers stability issue - run manually with: ./gradlew test --tests 'DlqIntegrationTest'")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DlqIntegrationTest {

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
    private KafkaTemplate<String, TelemetryRecorded> kafkaTemplate;

    @Autowired
    private DlqManagementService dlqManagementService;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, TelemetryRecorded> dlqConsumer;

    @BeforeEach
    void setUp() {
        // Create DLQ consumer for verification
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(),
                "dlq-test-group",
                "true"
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TelemetryRecorded.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ConsumerFactory<String, TelemetryRecorded> consumerFactory = 
                new DefaultKafkaConsumerFactory<>(consumerProps);
        dlqConsumer = consumerFactory.createConsumer();
        dlqConsumer.subscribe(Collections.singletonList("telemetry.recorded.dlq"));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (dlqConsumer != null) {
            dlqConsumer.close(Duration.ofSeconds(2));
        }
    }

    @Test
    void shouldExposeDlqAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dlq"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldHandleEndToEndDlqFlow() throws Exception {
        // Given - Create an event that will cause consumer to fail
        UUID eventId = UUID.randomUUID();
        TelemetryRecorded invalidEvent = new TelemetryRecorded(
                eventId,
                null, // This will cause consumer to fail
                new BigDecimal("25.5"),
                Instant.now(),
                Instant.now()
        );

        // When - Publish invalid message to main topic
        kafkaTemplate.send("telemetry.recorded", "invalid", invalidEvent).get();

        // Then - Wait for message to appear in DLQ after retries
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    ConsumerRecords<String, TelemetryRecorded> records = 
                            dlqConsumer.poll(Duration.ofMillis(1000));
                    
                    assertThat(records.count()).isGreaterThan(0);
                    
                    ConsumerRecord<String, TelemetryRecorded> dlqRecord = records.iterator().next();
                    assertThat(dlqRecord.value().getEventId()).isEqualTo(eventId);
                    assertThat(dlqRecord.headers().lastHeader("exception-message")).isNotNull();
                });

        // Verify DLQ messages can be listed
        mockMvc.perform(get("/api/v1/admin/dlq"))
                .andExpect(status().isOk());

        // Verify DLQ messages can be reprocessed
        mockMvc.perform(post("/api/v1/admin/dlq/reprocess"))
                .andExpect(status().isAccepted());
    }
}
