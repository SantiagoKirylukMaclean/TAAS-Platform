package com.koni.telemetry.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for Circuit Breaker pattern implementation.
 * Provides resilience against downstream service failures (Kafka).
 * 
 * Circuit Breaker States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Failure threshold exceeded, requests fail fast
 * - HALF_OPEN: Testing if service recovered, limited requests allowed
 */
@Configuration
public class CircuitBreakerConfiguration {
    
    /**
     * Creates CircuitBreakerConfig with custom settings for Kafka resilience.
     * 
     * Configuration:
     * - Sliding window: 10 requests (COUNT_BASED)
     * - Failure threshold: 50% (circuit opens if 5 out of 10 requests fail)
     * - Wait duration in OPEN state: 10 seconds
     * - Permitted calls in HALF_OPEN: 3 (to test recovery)
     * - Automatic transition: OPEN -> HALF_OPEN after wait duration
     * 
     * @return CircuitBreakerConfig with custom settings
     */
    @Bean
    public CircuitBreakerConfig kafkaCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            // Record all exceptions as failures (default behavior)
            // This allows the circuit breaker to count any RuntimeException as a failure
            .build();
    }
    
    /**
     * Creates CircuitBreakerRegistry with the custom configuration.
     * The registry manages all circuit breaker instances.
     * 
     * @param config the circuit breaker configuration
     * @return CircuitBreakerRegistry instance
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerConfig config) {
        return CircuitBreakerRegistry.of(config);
    }
    
    /**
     * Creates a CircuitBreaker instance named "kafka" for protecting Kafka operations.
     * This circuit breaker will be used by the ResilientKafkaEventPublisher.
     * 
     * @param registry the circuit breaker registry
     * @return CircuitBreaker instance for Kafka operations
     */
    @Bean
    public CircuitBreaker kafkaCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("kafka");
    }
}
