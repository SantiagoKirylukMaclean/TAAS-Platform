package com.koni.telemetry.application.service;

import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.domain.repository.FallbackEventRepository;
import com.koni.telemetry.infrastructure.persistence.entity.FallbackEventEntity;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for replaying events from the fallback repository to Kafka.
 * This service is used to recover from Kafka outages by replaying events
 * that were stored in the database when the circuit breaker was open.
 * 
 * The replay process:
 * 1. Check if circuit breaker is closed (Kafka is available)
 * 2. Retrieve all events from fallback repository
 * 3. Publish each event to Kafka
 * 4. Delete successfully published events from fallback repository
 * 
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FallbackReplayService {
    
    private final FallbackEventRepository fallbackRepository;
    private final KafkaTemplate<String, TelemetryRecorded> kafkaTemplate;
    private final CircuitBreaker circuitBreaker;
    private static final int TIMEOUT_SECONDS = 10;
    
    /**
     * Replays all events from the fallback repository to Kafka.
     * This method should only be called when the circuit breaker is closed
     * (i.e., Kafka is available).
     * 
     * The method will:
     * - Check if circuit breaker is closed
     * - Retrieve all fallback events
     * - Publish each event to Kafka
     * - Delete successfully published events
     * 
     * If the circuit breaker is not closed, the method will log a warning
     * and return without attempting to replay events.
     * 
     * @return the number of events successfully replayed
     */
    @Transactional
    public int replayEvents() {
        // Check if circuit breaker is closed
        CircuitBreaker.State state = circuitBreaker.getState();
        if (state != CircuitBreaker.State.CLOSED) {
            log.warn("Cannot replay events: circuit breaker is in {} state. Wait for circuit to close.", state);
            return 0;
        }
        
        log.info("Starting fallback event replay. Circuit breaker state: {}", state);
        
        // Retrieve all fallback events
        List<FallbackEventEntity> fallbackEvents = fallbackRepository.findAll();
        
        if (fallbackEvents.isEmpty()) {
            log.info("No fallback events to replay");
            return 0;
        }
        
        log.info("Found {} fallback events to replay", fallbackEvents.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        // Replay each event
        for (FallbackEventEntity fallbackEvent : fallbackEvents) {
            try {
                // Convert fallback entity to domain event
                TelemetryRecorded event = new TelemetryRecorded(
                        fallbackEvent.getEventId(),
                        fallbackEvent.getDeviceId(),
                        fallbackEvent.getMeasurement(),
                        fallbackEvent.getDate(),
                        fallbackEvent.getFailedAt()
                );
                
                // Publish to Kafka
                publishToKafka(event);
                
                // Delete from fallback repository after successful publish
                fallbackRepository.delete(fallbackEvent.getEventId());
                
                successCount++;
                log.info("Successfully replayed and deleted fallback event: eventId={}, deviceId={}", 
                        fallbackEvent.getEventId(), fallbackEvent.getDeviceId());
                
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to replay fallback event: eventId={}, deviceId={}. Event will remain in fallback repository.", 
                        fallbackEvent.getEventId(), fallbackEvent.getDeviceId(), e);
                // Continue with next event - don't fail the entire replay
            }
        }
        
        log.info("Fallback event replay completed: {} succeeded, {} failed", successCount, failureCount);
        
        return successCount;
    }
    
    /**
     * Publishes an event to Kafka.
     * This method waits for the publish to complete with a timeout.
     * 
     * @param event the event to publish
     * @throws RuntimeException if Kafka publish fails
     */
    private void publishToKafka(TelemetryRecorded event) {
        String topic = "telemetry.recorded";
        String key = event.getDeviceId().toString();
        
        try {
            // Send message asynchronously and wait for result
            CompletableFuture<SendResult<String, TelemetryRecorded>> future = 
                    kafkaTemplate.send(topic, key, event);
            
            // Wait for the send to complete with timeout
            SendResult<String, TelemetryRecorded> result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            log.debug("Successfully published replayed event to Kafka: topic={}, partition={}, offset={}, deviceId={}", 
                    topic, 
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.getDeviceId());
            
        } catch (Exception e) {
            log.error("Failed to publish replayed event to Kafka: deviceId={}, eventId={}", 
                    event.getDeviceId(), event.getEventId(), e);
            throw new RuntimeException("Failed to publish event to Kafka: " + e.getMessage(), e);
        }
    }
}
