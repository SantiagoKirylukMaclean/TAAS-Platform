package com.koni.telemetry.infrastructure.messaging;

import com.koni.telemetry.infrastructure.observability.TelemetryMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka error handling configuration for Dead Letter Queue (DLQ) support.
 * 
 * This configuration implements a robust error handling strategy:
 * - Exponential backoff retry strategy (1s, 2s, 4s)
 * - Maximum 3 retry attempts before sending to DLQ
 * - Dead Letter Queue topic: "telemetry.recorded.dlq"
 * - Retry count tracking in message headers
 *
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaErrorHandlingConfig {
    
    private static final String DLQ_TOPIC = "telemetry.recorded.dlq";
    private static final long INITIAL_INTERVAL = 1000L; // 1 second
    private static final double MULTIPLIER = 2.0; // doubles each retry
    private static final int MAX_ATTEMPTS = 3;
    
    private final TelemetryMetrics metrics;
    
    /**
     * Creates a DefaultErrorHandler with Dead Letter Queue support.
     * 
     * Error handling flow:
     * 1. First attempt fails → wait 1s → retry
     * 2. Second attempt fails → wait 2s → retry
     * 3. Third attempt fails → wait 4s → retry
     * 4. After 3 retries → send to DLQ topic "telemetry.recorded.dlq"
     * 
     * The error handler adds retry count to message headers and logs all failures.
     * 
     * @param kafkaTemplate the Kafka template for publishing to DLQ
     * @return configured DefaultErrorHandler with DLQ support
     */
    @Bean
    public CommonErrorHandler errorHandler(
            KafkaTemplate<?, ?> kafkaTemplate) {
        
        // Configure Dead Letter Publishing Recoverer (Requirement 9.1, 9.3)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (consumerRecord, exception) -> {
                    // Log error with message details and trace ID (Requirement 9.5)
                    log.error("Sending message to DLQ after {} retries. Topic: {}, Key: {}, Value: {}, Error: {}",
                            MAX_ATTEMPTS,
                            consumerRecord.topic(),
                            consumerRecord.key(),
                            consumerRecord.value(),
                            exception.getMessage(),
                            exception);
                    
                    // Record DLQ metric (Requirement 1.2)
                    metrics.recordDlqMessageSent();
                    
                    // Return DLQ topic partition (Requirement 9.3)
                    return new TopicPartition(DLQ_TOPIC, -1);
                }
        );
        
        // Configure exponential backoff (Requirement 9.4)
        // Initial: 1s, Multiplier: 2.0, Max attempts: 3
        // Retry delays: 1s, 2s, 4s
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_INTERVAL, MULTIPLIER);
        backOff.setMaxAttempts(MAX_ATTEMPTS);
        
        // Create DefaultErrorHandler with recoverer and backoff
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        
        // Add retry count to message headers (Requirement 9.2)
        errorHandler.setRetryListeners((consumerRecord, exception, deliveryAttempt) -> {
            log.warn("Retry attempt {} for message: topic={}, key={}, value={}, error={}",
                    deliveryAttempt,
                    consumerRecord.topic(),
                    consumerRecord.key(),
                    consumerRecord.value(),
                    exception.getMessage());
            
            // Add retry count to headers for tracking (Requirement 9.2)
            consumerRecord.headers().add("retry-count", 
                    String.valueOf(deliveryAttempt).getBytes());
            
            // Add exception message to headers (Requirement 9.2)
            consumerRecord.headers().add("exception-message",
                    exception.getMessage().getBytes());
        });
        
        return errorHandler;
    }
}
