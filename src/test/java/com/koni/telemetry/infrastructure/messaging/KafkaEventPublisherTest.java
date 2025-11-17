package com.koni.telemetry.infrastructure.messaging;

import com.koni.telemetry.domain.event.TelemetryRecorded;
import com.koni.telemetry.domain.exception.KafkaUnavailableException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KafkaEventPublisher.
 * Tests event publishing, partition key usage, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {
    
    @Mock
    private KafkaTemplate<String, TelemetryRecorded> kafkaTemplate;
    
    private KafkaEventPublisher publisher;
    private static final String TOPIC = "telemetry.recorded";
    
    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher(kafkaTemplate, TOPIC);
    }
    
    @Test
    void shouldPublishEventSuccessfully() {
        // Given
        TelemetryRecorded event = new TelemetryRecorded(
                UUID.randomUUID(),
                1L,
                new BigDecimal("25.5"),
                Instant.now(),
                Instant.now()
        );
        
        // Mock successful send
        CompletableFuture<SendResult<String, TelemetryRecorded>> future = new CompletableFuture<>();
        ProducerRecord<String, TelemetryRecorded> producerRecord = 
                new ProducerRecord<>(TOPIC, "1", event);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TOPIC, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, TelemetryRecorded> sendResult = 
                new SendResult<>(producerRecord, metadata);
        future.complete(sendResult);
        
        when(kafkaTemplate.send(eq(TOPIC), eq("1"), eq(event))).thenReturn(future);
        
        // When
        publisher.publish(event);
        
        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), keyCaptor.capture(), eq(event));
        assertThat(keyCaptor.getValue()).isEqualTo("1"); // deviceId as partition key
    }
    
    @Test
    void shouldUseDeviceIdAsPartitionKey() {
        // Given
        Long deviceId = 42L;
        TelemetryRecorded event = new TelemetryRecorded(
                UUID.randomUUID(),
                deviceId,
                new BigDecimal("30.0"),
                Instant.now(),
                Instant.now()
        );
        
        // Mock successful send
        CompletableFuture<SendResult<String, TelemetryRecorded>> future = new CompletableFuture<>();
        ProducerRecord<String, TelemetryRecorded> producerRecord = 
                new ProducerRecord<>(TOPIC, deviceId.toString(), event);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TOPIC, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, TelemetryRecorded> sendResult = 
                new SendResult<>(producerRecord, metadata);
        future.complete(sendResult);
        
        when(kafkaTemplate.send(eq(TOPIC), eq(deviceId.toString()), eq(event))).thenReturn(future);
        
        // When
        publisher.publish(event);
        
        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), keyCaptor.capture(), eq(event));
        assertThat(keyCaptor.getValue()).isEqualTo("42");
    }
    
    @Test
    void shouldThrowKafkaUnavailableExceptionWhenSendFails() {
        // Given
        TelemetryRecorded event = new TelemetryRecorded(
                UUID.randomUUID(),
                1L,
                new BigDecimal("25.5"),
                Instant.now(),
                Instant.now()
        );
        
        // Mock failed send
        CompletableFuture<SendResult<String, TelemetryRecorded>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka connection failed"));
        
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);
        
        // When/Then
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(KafkaUnavailableException.class)
                .hasMessageContaining("Failed to publish event to Kafka");
    }
    
    @Test
    void shouldThrowIllegalArgumentExceptionWhenEventIsNull() {
        // When/Then
        assertThatThrownBy(() -> publisher.publish(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event cannot be null");
        
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
