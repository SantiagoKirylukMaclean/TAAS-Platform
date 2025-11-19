package com.koni.telemetry.infrastructure.messaging;

import com.koni.telemetry.application.port.EventPublisher;
import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.domain.repository.FallbackEventRepository;
import com.koni.telemetry.infrastructure.persistence.entity.FallbackEventEntity;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.tracing.annotation.ContinueSpan;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Resilient Kafka implementation of the EventPublisher port with Circuit Breaker pattern.
 * This adapter publishes TelemetryRecorded events to Kafka with resilience against failures.
 * 
 * Features:
 * - Circuit Breaker protection to prevent cascading failures
 * - Fallback mechanism that stores events in database when Kafka is unavailable
 * - Uses deviceId as partition key to maintain ordering per device
 * - Logs circuit breaker state changes for observability
 * 
 * Circuit Breaker States:
 * - CLOSED: Normal operation, events published to Kafka
 * - OPEN: Kafka unavailable, events stored in fallback repository
 * - HALF_OPEN: Testing recovery, limited events sent to Kafka
 */
@Slf4j
@Service
@Primary
public class ResilientKafkaEventPublisher implements EventPublisher {
    
    private final KafkaTemplate<String, TelemetryRecorded> kafkaTemplate;
    private final CircuitBreaker circuitBreaker;
    private final FallbackEventRepository fallbackRepository;
    private final String topic;
    private static final int TIMEOUT_SECONDS = 10;
    
    public ResilientKafkaEventPublisher(
            KafkaTemplate<String, TelemetryRecorded> kafkaTemplate,
            CircuitBreaker kafkaCircuitBreaker,
            FallbackEventRepository fallbackRepository,
            @Value("${telemetry.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreaker = kafkaCircuitBreaker;
        this.fallbackRepository = fallbackRepository;
        this.topic = topic;
        
        // Register event listeners for circuit breaker state changes
        registerCircuitBreakerEventListeners();
    }
    
    /**
     * Publishes a TelemetryRecorded event to Kafka with circuit breaker protection.
     * If the circuit is open or Kafka is unavailable, the event is stored in the fallback repository.
     * 
     * @param event the TelemetryRecorded event to publish
     * @throws IllegalArgumentException if event is null
     */
    @Override
    @ContinueSpan(log = "kafka-publish")
    public void publish(@SpanTag("deviceId") TelemetryRecorded event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        
        log.debug("Publishing TelemetryRecorded event with circuit breaker: deviceId={}, eventId={}", 
                event.getDeviceId(), event.getEventId());
        
        // Wrap Kafka publish operation with circuit breaker
        Supplier<Void> kafkaPublishSupplier = () -> {
            try {
                publishToKafka(event);
                return null;
            } catch (Exception e) {
                // Re-throw as RuntimeException so circuit breaker can record it
                log.error("Kafka publish failed, circuit breaker will record this failure: deviceId={}, eventId={}", 
                        event.getDeviceId(), event.getEventId(), e);
                throw new RuntimeException("Kafka publish failed", e);
            }
        };
        
        try {
            // Decorate the supplier with circuit breaker
            Supplier<Void> decoratedSupplier = CircuitBreaker.decorateSupplier(
                    circuitBreaker, 
                    kafkaPublishSupplier
            );
            
            // Execute the decorated supplier
            decoratedSupplier.get();
            
        } catch (CallNotPermittedException e) {
            // Circuit breaker is open - fail fast and use fallback
            log.warn("Circuit breaker is OPEN, using fallback for event: deviceId={}, eventId={}", 
                    event.getDeviceId(), event.getEventId());
            handleFallback(event);
            
        } catch (Exception e) {
            // Kafka publish failed - circuit breaker has recorded the failure, now use fallback
            log.warn("Kafka publish failed (circuit breaker recorded), using fallback: deviceId={}, eventId={}", 
                    event.getDeviceId(), event.getEventId());
            handleFallback(event);
        }
    }
    
    /**
     * Publishes an event directly to Kafka.
     * This method is wrapped by the circuit breaker.
     * 
     * @param event the event to publish
     * @throws RuntimeException if Kafka publish fails
     */
    private void publishToKafka(TelemetryRecorded event) {
        String key = event.getDeviceId().toString();
        
        try {
            // Send message asynchronously and wait for result
            CompletableFuture<SendResult<String, TelemetryRecorded>> future = 
                    kafkaTemplate.send(topic, key, event);
            
            // Wait for the send to complete with timeout
            SendResult<String, TelemetryRecorded> result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            log.info("Successfully published event to Kafka: topic={}, partition={}, offset={}, deviceId={}", 
                    topic, 
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.getDeviceId());
            
        } catch (Exception e) {
            log.error("Kafka publish failed: deviceId={}, eventId={}", 
                    event.getDeviceId(), event.getEventId(), e);
            throw new RuntimeException("Failed to publish event to Kafka: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles fallback when Kafka is unavailable or circuit breaker is open.
     * Stores the event in the fallback repository for later replay.
     * 
     * @param event the event to store in fallback repository
     */
    private void handleFallback(TelemetryRecorded event) {
        try {
            FallbackEventEntity fallbackEvent = new FallbackEventEntity(
                    event.getEventId(),
                    event.getDeviceId(),
                    event.getMeasurement(),
                    event.getDate(),
                    Instant.now()
            );
            
            fallbackRepository.save(fallbackEvent);
            
            log.info("Event stored in fallback repository: eventId={}, deviceId={}", 
                    event.getEventId(), event.getDeviceId());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to store event in fallback repository: eventId={}, deviceId={}", 
                    event.getEventId(), event.getDeviceId(), e);
            // This is a critical error - event may be lost
            throw new RuntimeException("Failed to store event in fallback repository", e);
        }
    }
    
    /**
     * Registers event listeners for circuit breaker state changes.
     * Logs all state transitions for observability and debugging.
     */
    private void registerCircuitBreakerEventListeners() {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Circuit breaker state transition: {} -> {} (failure rate: {}%, slow call rate: {}%)",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState(),
                            circuitBreaker.getMetrics().getFailureRate(),
                            circuitBreaker.getMetrics().getSlowCallRate());
                })
                .onSuccess(event -> {
                    log.debug("Circuit breaker recorded success: duration={}ms", 
                            event.getElapsedDuration().toMillis());
                })
                .onError(event -> {
                    log.warn("Circuit breaker recorded error: duration={}ms, error={}", 
                            event.getElapsedDuration().toMillis(),
                            event.getThrowable().getClass().getSimpleName());
                })
                .onCallNotPermitted(event -> {
                    log.warn("Circuit breaker call not permitted (circuit is OPEN)");
                });
    }
}
