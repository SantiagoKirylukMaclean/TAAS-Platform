package com.koni.telemetry.infrastructure.tracing;

import brave.Span;
import brave.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;

/**
 * Kafka producer interceptor that propagates distributed tracing context.
 * Adds trace ID and span ID to Kafka message headers for end-to-end tracing.
 * 
 * This enables correlation of requests across HTTP, application logic, and Kafka messaging.
 */
@Slf4j
@RequiredArgsConstructor
public class TracingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    
    private final Tracer tracer;
    
    /**
     * Intercepts outgoing Kafka messages and adds tracing headers.
     * Extracts the current span context and adds X-B3-TraceId and X-B3-SpanId headers.
     */
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        Span span = tracer.currentSpan();
        
        if (span != null) {
            // Add B3 trace context headers to Kafka message
            String traceId = span.context().traceIdString();
            String spanId = span.context().spanIdString();
            
            record.headers().add("X-B3-TraceId", traceId.getBytes());
            record.headers().add("X-B3-SpanId", spanId.getBytes());
            
            log.debug("Added trace context to Kafka message: traceId={}, spanId={}", traceId, spanId);
        } else {
            log.debug("No active span found, skipping trace context propagation");
        }
        
        return record;
    }
    
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // No action needed on acknowledgement
    }
    
    @Override
    public void close() {
        // No resources to clean up
    }
    
    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration needed
    }
}
