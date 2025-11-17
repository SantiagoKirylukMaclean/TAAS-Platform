package com.koni.telemetry.infrastructure.messaging;

import com.koni.telemetry.domain.event.TelemetryRecorded;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for consuming TelemetryRecorded events.
 * 
 * Configuration features:
 * - JSON deserialization for event payloads
 * - Manual offset commit for at-least-once processing
 * - Auto-offset-reset to earliest for consuming from beginning
 * - Trusted packages configured for JSON deserialization
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    
    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;
    
    /**
     * Creates a ConsumerFactory for TelemetryRecorded events.
     * Configures JSON deserialization and manual offset commit.
     */
    @Bean
    public ConsumerFactory<String, TelemetryRecorded> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Bootstrap servers
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Consumer group configuration
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Offset management
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Deserializers
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // JSON deserializer settings
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TelemetryRecorded.class.getName());
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    /**
     * Creates a KafkaListenerContainerFactory for consuming TelemetryRecorded events.
     * Configures manual acknowledgment mode for at-least-once processing.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TelemetryRecorded> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TelemetryRecorded> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Enable manual acknowledgment for at-least-once processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        return factory;
    }
}
