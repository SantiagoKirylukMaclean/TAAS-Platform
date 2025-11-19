package com.koni.telemetry.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CircuitBreakerConfiguration.
 * Verifies that circuit breaker beans are created with correct settings.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "spring.kafka.consumer.auto-startup=false",
    "spring.kafka.producer.bootstrap-servers=localhost:9092"
})
class CircuitBreakerConfigurationTest {
    
    @MockBean
    private KafkaTemplate<?, ?> kafkaTemplate;
    
    @Autowired
    private CircuitBreakerConfig kafkaCircuitBreakerConfig;
    
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Autowired
    private CircuitBreaker kafkaCircuitBreaker;
    
    @Test
    void shouldCreateCircuitBreakerConfigBean() {
        assertThat(kafkaCircuitBreakerConfig).isNotNull();
    }
    
    @Test
    void shouldConfigureCircuitBreakerWithCorrectSettings() {
        assertThat(kafkaCircuitBreakerConfig.getSlidingWindowType())
            .isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(kafkaCircuitBreakerConfig.getSlidingWindowSize()).isEqualTo(10);
        assertThat(kafkaCircuitBreakerConfig.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(kafkaCircuitBreakerConfig.getWaitIntervalFunctionInOpenState().apply(1))
            .isEqualTo(Duration.ofSeconds(10).toMillis());
        assertThat(kafkaCircuitBreakerConfig.getPermittedNumberOfCallsInHalfOpenState())
            .isEqualTo(3);
        assertThat(kafkaCircuitBreakerConfig.isAutomaticTransitionFromOpenToHalfOpenEnabled())
            .isTrue();
    }
    
    @Test
    void shouldCreateCircuitBreakerRegistryBean() {
        assertThat(circuitBreakerRegistry).isNotNull();
    }
    
    @Test
    void shouldCreateKafkaCircuitBreakerBean() {
        assertThat(kafkaCircuitBreaker).isNotNull();
        assertThat(kafkaCircuitBreaker.getName()).isEqualTo("kafka");
    }
    
    @Test
    void shouldHaveKafkaCircuitBreakerInClosedState() {
        assertThat(kafkaCircuitBreaker.getState())
            .isEqualTo(CircuitBreaker.State.CLOSED);
    }
    
    @Test
    void shouldRegisterKafkaCircuitBreakerInRegistry() {
        CircuitBreaker registeredCircuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka");
        assertThat(registeredCircuitBreaker).isNotNull();
        assertThat(registeredCircuitBreaker).isSameAs(kafkaCircuitBreaker);
    }
}
