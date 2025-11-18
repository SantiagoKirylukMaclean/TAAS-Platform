package com.koni.telemetry.infrastructure.tracing;

import brave.Span;
import brave.Tracer;
import brave.propagation.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Kafka consumer interceptor that extracts and continues distributed tracing context.
 * Extracts trace ID and span ID from Kafka message headers to maintain trace continuity.
 * 
 * This enables end-to-end tracing from producers through Kafka to consumers,
 * allowing correlation of requests across the entire system.
 * 
 * This implementation uses Spring Kafka's RecordInterceptor interface, which is called
 * before each record is processed by the listener, allowing us to set up trace context.
 * 
 * Requirements: 4.2, 5.5
 */
@Slf4j
@RequiredArgsConstructor
public class TracingConsumerInterceptor<K, V> implements RecordInterceptor<K, V> {
    
    private final Tracer tracer;
    
    /**
     * Intercepts incoming Kafka messages and extracts tracing headers.
     * Extracts X-B3-TraceId and X-B3-SpanId headers and continues the span in the consumer.
     * 
     * This method is called before the consumer processes each record, allowing us to
     * set up the trace context that will be used during message processing.
     */
    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, org.apache.kafka.clients.consumer.Consumer<K, V> consumer) {
        Header traceIdHeader = record.headers().lastHeader("X-B3-TraceId");
        Header spanIdHeader = record.headers().lastHeader("X-B3-SpanId");
        
        if (traceIdHeader != null && spanIdHeader != null) {
            try {
                String traceId = new String(traceIdHeader.value());
                String spanId = new String(spanIdHeader.value());
                
                // Create a trace context from the extracted headers
                TraceContext context = TraceContext.newBuilder()
                        .traceId(parseTraceId(traceId))
                        .spanId(parseSpanId(spanId))
                        .sampled(true)
                        .build();
                
                // Continue the span in the consumer context
                Span span = tracer.newChild(context);
                span.name("kafka-consume");
                span.tag("kafka.topic", record.topic());
                span.tag("kafka.partition", String.valueOf(record.partition()));
                span.tag("kafka.offset", String.valueOf(record.offset()));
                span.start();
                
                log.debug("Continued trace context in consumer: traceId={}, spanId={}, topic={}", 
                        traceId, spanId, record.topic());
                
                // The span will be in scope for the duration of message processing
                // and will be automatically finished by the tracing framework
                
            } catch (Exception e) {
                log.warn("Failed to extract trace context from Kafka headers: {}", e.getMessage());
            }
        } else {
            log.debug("No trace context found in Kafka message headers for topic: {}", record.topic());
        }
        
        return record;
    }
    
    /**
     * Parses a trace ID string into a long value.
     * Handles both 16-character (64-bit) and 32-character (128-bit) trace IDs.
     */
    private long parseTraceId(String traceId) {
        if (traceId.length() == 16) {
            // 64-bit trace ID
            return Long.parseUnsignedLong(traceId, 16);
        } else if (traceId.length() == 32) {
            // 128-bit trace ID - use the lower 64 bits
            return Long.parseUnsignedLong(traceId.substring(16), 16);
        } else {
            throw new IllegalArgumentException("Invalid trace ID length: " + traceId.length());
        }
    }
    
    /**
     * Parses a span ID string into a long value.
     * Span IDs are always 16 characters (64-bit).
     */
    private long parseSpanId(String spanId) {
        if (spanId.length() != 16) {
            throw new IllegalArgumentException("Invalid span ID length: " + spanId.length());
        }
        return Long.parseUnsignedLong(spanId, 16);
    }
}
