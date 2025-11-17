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
 * - Manual acknowledgment mode for offset control
 * - Auto-offset-reset to earliest for reliability
 * - Trusted packages configuration for JSON deserialization
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    
    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;
    
    /**
     * Creates a ConsumerFactory for TelemetryRecorded events.
     * Configures JSON deserialization and consumer settings.
     */
    @Bean
    public ConsumerFactory<String, TelemetryRecorded> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Bootstrap servers
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Consumer group
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Offset management
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
        
        // Deserializers
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // JSON deserializer settings
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TelemetryRecorded.class.getName());
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        return new DefaultKafkaConsumerFactory<>(
                configProps,
                new StringDeserializer(),
                new JsonDeserializer<>(TelemetryRecorded.class, false)
        );
    }
    
    /**
     * Creates a KafkaListenerContainerFactory with manual acknowledgment mode.
     * This allows the consumer to manually commit offsets after successful processing.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TelemetryRecorded> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TelemetryRecorded> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // Enable manual acknowledgment mode for offset control
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        return factory;
    }
}
