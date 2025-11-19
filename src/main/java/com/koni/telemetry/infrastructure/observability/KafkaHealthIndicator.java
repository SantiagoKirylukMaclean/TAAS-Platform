package com.koni.telemetry.infrastructure.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Health indicator for Kafka connectivity.
 * 
 * Tests Kafka availability by attempting to describe the cluster.
 * Returns UP if Kafka is reachable, DOWN if connection fails.
 * 
 * This health check is used for:
 * - Kubernetes readiness probes
 * - Monitoring dashboards
 * - Circuit breaker decisions
 * 
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {
    
    private final KafkaAdmin kafkaAdmin;
    
    /**
     * Checks Kafka health by attempting to describe the cluster.
     * 
     * The check:
     * 1. Creates an AdminClient from KafkaAdmin configuration
     * 2. Attempts to describe the cluster with a 5-second timeout
     * 3. Returns UP if successful, DOWN if any exception occurs
     * 
     * @return Health status with cluster details or error information
     */
    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            
            // Attempt to get cluster ID with timeout
            String clusterId = clusterResult.clusterId().get(5, TimeUnit.SECONDS);
            int nodeCount = clusterResult.nodes().get(5, TimeUnit.SECONDS).size();
            
            log.debug("Kafka health check passed: clusterId={}, nodes={}", clusterId, nodeCount);
            
            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodeCount", nodeCount)
                    .build();
                    
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }
}
