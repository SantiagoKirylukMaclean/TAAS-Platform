package com.koni.telemetry.infrastructure.messaging;

import com.koni.telemetry.application.port.EventPublisher;
import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.domain.exception.KafkaUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka implementation of the EventPublisher port.
 * This adapter publishes TelemetryRecorded events to a Kafka topic.
 * 
 * Features:
 * - Uses deviceId as partition key to maintain ordering per device
 * - Implements retry logic (3 attempts via Kafka producer configuration)
 * - Handles Kafka unavailability with KafkaUnavailableException
 */
@Slf4j
@Service
public class KafkaEventPublisher implements EventPublisher {
    
    private final KafkaTemplate<String, TelemetryRecorded> kafkaTemplate;
    private final String topic;
    private static final int TIMEOUT_SECONDS = 10;
    
    public KafkaEventPublisher(
            KafkaTemplate<String, TelemetryRecorded> kafkaTemplate,
            @Value("${telemetry.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }
    
    /**
     * Publishes a TelemetryRecorded event to Kafka.
     * Uses deviceId as the partition key to ensure ordering per device.
     * 
     * @param event the TelemetryRecorded event to publish
     * @throws IllegalArgumentException if event is null
     * @throws KafkaUnavailableException if Kafka is unavailable after retries
     */
    @Override
    public void publish(TelemetryRecorded event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        
        String key = event.getDeviceId().toString();
        
        log.debug("Publishing TelemetryRecorded event: deviceId={}, eventId={}", 
                event.getDeviceId(), event.getEventId());
        
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
            log.error("Failed to publish event to Kafka after retries: deviceId={}, eventId={}", 
                    event.getDeviceId(), event.getEventId(), e);
            throw new KafkaUnavailableException(
                    "Failed to publish event to Kafka: " + e.getMessage(), e);
        }
    }
}
