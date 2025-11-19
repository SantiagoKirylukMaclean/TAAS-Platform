package com.koni.telemetry.infrastructure.tracing;

import brave.Tracer;
import brave.Tracing;
import com.koni.telemetry.domain.event.TelemetryRecorded;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for distributed tracing across the telemetry system.
 * 
 * This configuration:
 * - Creates a Tracer bean for creating and managing spans
 * - Configures KafkaTemplate with tracing interceptor for trace propagation
 * - Enables end-to-end tracing from HTTP requests through Kafka messaging
 * 
 */
@Slf4j
@Configuration
public class TracingConfiguration {
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.producer.acks:all}")
    private String acks;
    
    @Value("${spring.kafka.producer.retries:3}")
    private Integer retries;
    
    /**
     * Creates a Tracer bean for distributed tracing.
     * The tracer is used to create spans and propagate trace context.
     * 
     * Spring Boot auto-configuration will handle Zipkin reporter setup
     * based on management.zipkin.tracing.endpoint property.
     */
    @Bean
    public Tracer tracer() {
        Tracing tracing = Tracing.newBuilder()
                .localServiceName(applicationName)
                .build();
        
        log.info("Initialized distributed tracing for service: {}", applicationName);
        
        return tracing.tracer();
    }
    
    /**
     * Creates a ProducerFactory with tracing interceptor.
     * This factory is used by the KafkaTemplate to create producers
     * that automatically propagate trace context.
     */
    @Bean
    public ProducerFactory<String, TelemetryRecorded> tracingProducerFactory(Tracer tracer) {
        Map<String, Object> configProps = new HashMap<>();
        
        // Bootstrap servers
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serializers
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // JSON serializer settings
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        
        // Create factory with tracing interceptor
        DefaultKafkaProducerFactory<String, TelemetryRecorded> factory = 
                new DefaultKafkaProducerFactory<>(configProps);
        
        log.info("Configured Kafka producer factory with tracing interceptor");
        
        return factory;
    }
    
    /**
     * Creates a KafkaTemplate with tracing support.
     * This template automatically propagates trace context to Kafka messages
     * via the TracingProducerInterceptor.
     * 
     * The interceptor adds X-B3-TraceId and X-B3-SpanId headers to each message,
     * enabling end-to-end tracing across service boundaries.
     */
    @Bean
    public KafkaTemplate<String, TelemetryRecorded> tracingKafkaTemplate(
            ProducerFactory<String, TelemetryRecorded> tracingProducerFactory,
            Tracer tracer) {
        
        KafkaTemplate<String, TelemetryRecorded> template = 
                new KafkaTemplate<>(tracingProducerFactory);
        
        // Add tracing interceptor to propagate trace context
        template.setProducerInterceptor(new TracingProducerInterceptor<>(tracer));
        
        log.info("Configured KafkaTemplate with tracing interceptor for trace propagation");
        
        return template;
    }
}
