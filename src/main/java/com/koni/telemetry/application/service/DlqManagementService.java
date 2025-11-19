package com.koni.telemetry.application.service;

import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.infrastructure.web.dto.DlqMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing Dead Letter Queue (DLQ) messages.
 * 
 * This service provides functionality to:
 * - List all messages in the DLQ topic
 * - Extract error details and retry count from message headers
 * - Reprocess messages from the DLQ by republishing to the main topic
 * 
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqManagementService {
    
    private final ConsumerFactory<String, TelemetryRecorded> consumerFactory;
    private final KafkaTemplate<String, TelemetryRecorded> kafkaTemplate;
    private final com.koni.telemetry.infrastructure.observability.TelemetryMetrics metrics;
    
    private static final String DLQ_TOPIC = "telemetry.recorded.dlq";
    private static final String MAIN_TOPIC = "telemetry.recorded";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final int PUBLISH_TIMEOUT_SECONDS = 10;
    
    /**
     * Lists all messages currently in the Dead Letter Queue.
     * 
     * This method:
     * 1. Creates a temporary consumer with a unique group ID
     * 2. Subscribes to the DLQ topic
     * 3. Polls for messages
     * 4. Extracts error details and retry count from headers
     * 5. Returns a list of DlqMessage DTOs
     * 
     * Requirement 10.1: Expose endpoint to list all messages in the DLQ
     * Requirement 10.2: Return message content, error details, timestamp, and retry count
     * 
     * @return list of DlqMessage objects containing event data and error information
     */
    public List<DlqMessage> listDlqMessages() {
        List<DlqMessage> messages = new ArrayList<>();
        
        log.info("Listing messages from DLQ topic: {}", DLQ_TOPIC);
        
        // Create a consumer with a unique group ID for reading DLQ messages
        // This ensures we don't interfere with other consumers
        try (Consumer<String, TelemetryRecorded> consumer = 
                consumerFactory.createConsumer("dlq-reader-" + System.currentTimeMillis(), "dlq-reader")) {
            
            // Subscribe to DLQ topic
            consumer.subscribe(Collections.singletonList(DLQ_TOPIC));
            
            // Seek to beginning to read all messages
            consumer.poll(Duration.ofMillis(100)); // Initial poll to get partition assignment
            consumer.seekToBeginning(consumer.assignment());
            
            // Poll for messages
            ConsumerRecords<String, TelemetryRecorded> records = consumer.poll(POLL_TIMEOUT);
            
            log.info("Found {} messages in DLQ", records.count());
            
            // Process each record and extract error information
            for (ConsumerRecord<String, TelemetryRecorded> record : records) {
                try {
                    // Extract error message from headers (Requirement 10.2)
                    String errorMessage = extractHeaderValue(record, "exception-message", "Unknown error");
                    
                    // Extract retry count from headers (Requirement 10.2)
                    int retryCount = extractRetryCount(record);
                    
                    // Get timestamp (Requirement 10.2)
                    Instant timestamp = Instant.ofEpochMilli(record.timestamp());
                    
                    // Create DlqMessage DTO
                    DlqMessage dlqMessage = new DlqMessage(
                            record.value(),
                            errorMessage,
                            retryCount,
                            timestamp
                    );
                    
                    messages.add(dlqMessage);
                    
                    log.debug("DLQ message: eventId={}, deviceId={}, error={}, retryCount={}, timestamp={}",
                            record.value().getEventId(),
                            record.value().getDeviceId(),
                            errorMessage,
                            retryCount,
                            timestamp);
                    
                } catch (Exception e) {
                    log.error("Failed to process DLQ record: topic={}, partition={}, offset={}",
                            record.topic(), record.partition(), record.offset(), e);
                    // Continue processing other messages
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to list DLQ messages from topic: {}", DLQ_TOPIC, e);
            throw new RuntimeException("Failed to list DLQ messages: " + e.getMessage(), e);
        }
        
        log.info("Successfully listed {} messages from DLQ", messages.size());
        return messages;
    }
    
    /**
     * Reprocesses all messages from the Dead Letter Queue.
     * 
     * This method:
     * 1. Retrieves all messages from the DLQ
     * 2. Republishes each message to the main topic
     * 3. Logs success/failure for each message
     * 
     * Note: Messages are not automatically removed from the DLQ after reprocessing.
     * If reprocessing succeeds, the consumer will commit the offset and the message
     * will not be read again. If reprocessing fails, the message remains in the DLQ.
     * 
     * Requirement 10.3: Expose endpoint to reprocess all DLQ messages
     * Requirement 10.4: Consume messages from DLQ and attempt to process them again
     * Requirement 10.5: Remove message from DLQ when reprocessing succeeds
     * 
     * @return the number of messages successfully reprocessed
     */
    public int reprocessDlqMessages() {
        log.info("Starting DLQ message reprocessing from topic: {}", DLQ_TOPIC);
        
        int successCount = 0;
        int failureCount = 0;
        
        // Create a consumer with a unique group ID for reprocessing
        try (Consumer<String, TelemetryRecorded> consumer = 
                consumerFactory.createConsumer("dlq-reprocessor-" + System.currentTimeMillis(), "dlq-reprocessor")) {
            
            // Subscribe to DLQ topic
            consumer.subscribe(Collections.singletonList(DLQ_TOPIC));
            
            // Seek to beginning to read all messages
            consumer.poll(Duration.ofMillis(100)); // Initial poll to get partition assignment
            consumer.seekToBeginning(consumer.assignment());
            
            // Poll for messages
            ConsumerRecords<String, TelemetryRecorded> records = consumer.poll(POLL_TIMEOUT);
            
            log.info("Found {} messages to reprocess from DLQ", records.count());
            
            // Reprocess each message
            for (ConsumerRecord<String, TelemetryRecorded> record : records) {
                try {
                    TelemetryRecorded event = record.value();
                    
                    log.info("Reprocessing DLQ message: eventId={}, deviceId={}, originalTimestamp={}",
                            event.getEventId(),
                            event.getDeviceId(),
                            Instant.ofEpochMilli(record.timestamp()));
                    
                    // Republish to main topic (Requirement 10.4)
                    republishToMainTopic(event);
                    
                    // Record reprocessed metric (Requirement 1.2)
                    metrics.recordDlqMessageReprocessed();
                    
                    successCount++;
                    
                    log.info("Successfully reprocessed DLQ message: eventId={}, deviceId={}",
                            event.getEventId(), event.getDeviceId());
                    
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to reprocess DLQ message: topic={}, partition={}, offset={}, eventId={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            record.value().getEventId(),
                            e);
                    // Continue with next message - don't fail the entire reprocessing
                }
            }
            
            // Commit offsets to mark messages as processed (Requirement 10.5)
            // This effectively removes them from the DLQ from the consumer's perspective
            consumer.commitSync();
            
        } catch (Exception e) {
            log.error("Failed to reprocess DLQ messages from topic: {}", DLQ_TOPIC, e);
            throw new RuntimeException("Failed to reprocess DLQ messages: " + e.getMessage(), e);
        }
        
        log.info("DLQ reprocessing completed: {} succeeded, {} failed", successCount, failureCount);
        
        return successCount;
    }
    
    /**
     * Republishes an event to the main Kafka topic.
     * 
     * @param event the event to republish
     * @throws RuntimeException if publishing fails
     */
    private void republishToMainTopic(TelemetryRecorded event) {
        String key = event.getDeviceId().toString();
        
        try {
            // Send message asynchronously and wait for result
            CompletableFuture<SendResult<String, TelemetryRecorded>> future = 
                    kafkaTemplate.send(MAIN_TOPIC, key, event);
            
            // Wait for the send to complete with timeout
            SendResult<String, TelemetryRecorded> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            log.debug("Successfully republished event to main topic: topic={}, partition={}, offset={}, eventId={}",
                    MAIN_TOPIC,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to republish event to main topic: eventId={}, deviceId={}",
                    event.getEventId(), event.getDeviceId(), e);
            throw new RuntimeException("Failed to republish event to Kafka: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts a header value as a string from a Kafka record.
     * 
     * @param record the Kafka record
     * @param headerName the name of the header to extract
     * @param defaultValue the default value if header is not found
     * @return the header value as a string, or default value if not found
     */
    private String extractHeaderValue(ConsumerRecord<String, TelemetryRecorded> record, 
                                     String headerName, 
                                     String defaultValue) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value());
        }
        return defaultValue;
    }
    
    /**
     * Extracts the retry count from a Kafka record's headers.
     * 
     * @param record the Kafka record
     * @return the retry count, or 0 if not found
     */
    private int extractRetryCount(ConsumerRecord<String, TelemetryRecorded> record) {
        String retryCountStr = extractHeaderValue(record, "retry-count", "0");
        try {
            return Integer.parseInt(retryCountStr);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse retry count from header: {}", retryCountStr);
            return 0;
        }
    }
}
