package com.koni.telemetry.infrastructure.observability;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KafkaHealthIndicator.
 * Tests UP and DOWN states with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {
    
    @Mock
    private KafkaAdmin kafkaAdmin;
    
    @Mock
    private AdminClient adminClient;
    
    @Mock
    private DescribeClusterResult describeClusterResult;
    
    @Mock
    private KafkaFuture<String> clusterIdFuture;
    
    @Mock
    private KafkaFuture<Collection<Node>> nodesFuture;
    
    private KafkaHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new KafkaHealthIndicator(kafkaAdmin);
    }
    
    @Test
    void shouldReturnUpWhenKafkaIsAvailable() throws Exception {
        // Given
        Map<String, Object> config = new HashMap<>();
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(config);
        
        String clusterId = "test-cluster-id";
        Collection<Node> nodes = Arrays.asList(
                new Node(1, "localhost", 9092),
                new Node(2, "localhost", 9093)
        );
        
        when(clusterIdFuture.get(anyLong(), any())).thenReturn(clusterId);
        when(nodesFuture.get(anyLong(), any())).thenReturn(nodes);
        when(describeClusterResult.clusterId()).thenReturn(clusterIdFuture);
        when(describeClusterResult.nodes()).thenReturn(nodesFuture);
        
        try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
            adminClientMock.when(() -> AdminClient.create(config)).thenReturn(adminClient);
            when(adminClient.describeCluster()).thenReturn(describeClusterResult);
            
            // When
            Health health = healthIndicator.health();
            
            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("clusterId", clusterId);
            assertThat(health.getDetails()).containsEntry("nodeCount", 2);
            
            verify(adminClient).close();
        }
    }
    
    @Test
    void shouldReturnDownWhenKafkaIsUnavailable() throws Exception {
        // Given
        Map<String, Object> config = new HashMap<>();
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(config);
        
        when(clusterIdFuture.get(anyLong(), any())).thenThrow(new ExecutionException("Connection refused", 
                new RuntimeException("Connection refused")));
        when(describeClusterResult.clusterId()).thenReturn(clusterIdFuture);
        
        try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
            adminClientMock.when(() -> AdminClient.create(config)).thenReturn(adminClient);
            when(adminClient.describeCluster()).thenReturn(describeClusterResult);
            
            // When
            Health health = healthIndicator.health();
            
            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsKey("error");
            assertThat(health.getDetails()).containsKey("message");
            assertThat(health.getDetails().get("error")).isEqualTo("ExecutionException");
            
            verify(adminClient).close();
        }
    }
    
    @Test
    void shouldReturnDownWhenDescribeClusterFails() throws Exception {
        // Given
        Map<String, Object> config = new HashMap<>();
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(config);
        
        try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
            adminClientMock.when(() -> AdminClient.create(config)).thenReturn(adminClient);
            when(adminClient.describeCluster()).thenThrow(new RuntimeException("Kafka error"));
            
            // When
            Health health = healthIndicator.health();
            
            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsKey("error");
            assertThat(health.getDetails()).containsKey("message");
            assertThat(health.getDetails().get("error")).isEqualTo("RuntimeException");
            assertThat(health.getDetails().get("message")).isEqualTo("Kafka error");
            
            verify(adminClient).close();
        }
    }
    
    @Test
    void shouldReturnDownWhenAdminClientCreationFails() {
        // Given
        Map<String, Object> config = new HashMap<>();
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(config);
        
        try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
            adminClientMock.when(() -> AdminClient.create(config))
                    .thenThrow(new RuntimeException("Failed to create admin client"));
            
            // When
            Health health = healthIndicator.health();
            
            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsKey("error");
            assertThat(health.getDetails()).containsKey("message");
        }
    }
    
    @Test
    void shouldCloseAdminClientEvenWhenExceptionOccurs() throws Exception {
        // Given
        Map<String, Object> config = new HashMap<>();
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(config);
        
        when(clusterIdFuture.get(anyLong(), any())).thenThrow(new RuntimeException("Test exception"));
        when(describeClusterResult.clusterId()).thenReturn(clusterIdFuture);
        
        try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
            adminClientMock.when(() -> AdminClient.create(config)).thenReturn(adminClient);
            when(adminClient.describeCluster()).thenReturn(describeClusterResult);
            
            // When
            healthIndicator.health();
            
            // Then
            verify(adminClient).close();
        }
    }
}
