package com.koni.telemetry.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for the telemetry system.
 * 
 * Defines the "telemetry.recorded" topic with:
 * - 3 partitions for parallel processing and scalability
 * - Replication factor of 1 (suitable for development; increase for production)
 * - Automatic topic creation on application startup
 * 
 * The topic uses deviceId as partition key to maintain ordering per device.
 */
@Configuration
public class KafkaTopicConfig {
    
    @Value("${telemetry.kafka.topic}")
    private String topicName;
    
    @Value("${telemetry.kafka.partitions:3}")
    private int partitions;
    
    @Value("${telemetry.kafka.replication-factor:1}")
    private short replicationFactor;
    
    /**
     * Creates the telemetry.recorded topic configuration.
     * 
     * Partitions: 3 (allows parallel processing of events from different devices)
     * Replication Factor: 1 (for development; should be 3 in production for fault tolerance)
     * 
     * The partition count can be increased later to scale throughput.
     * Events are partitioned by deviceId to guarantee ordering per device.
     * 
     * @return NewTopic configuration for telemetry.recorded
     */
    @Bean
    public NewTopic telemetryRecordedTopic() {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
