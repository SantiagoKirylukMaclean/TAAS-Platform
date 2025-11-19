package com.koni.telemetry.infrastructure.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PostgreSQLHealthIndicator.
 * Tests UP and DOWN states with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class PostgreSQLHealthIndicatorTest {
    
    @Mock
    private DataSource dataSource;
    
    @Mock
    private Connection connection;
    
    @Mock
    private Statement statement;
    
    @Mock
    private ResultSet resultSet;
    
    @Mock
    private DatabaseMetaData metaData;
    
    private PostgreSQLHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new PostgreSQLHealthIndicator(dataSource);
    }
    
    @Test
    void shouldReturnUpWhenDatabaseIsAvailable() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getDatabaseProductVersion()).thenReturn("14.5");
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
        assertThat(health.getDetails()).containsEntry("version", "14.5");
        
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }
    
    @Test
    void shouldReturnDownWhenConnectionFails() throws Exception {
        // Given
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails()).containsKey("message");
        assertThat(health.getDetails().get("error")).isEqualTo("SQLException");
        assertThat(health.getDetails().get("message")).isEqualTo("Connection refused");
    }
    
    @Test
    void shouldReturnDownWhenQueryFails() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenThrow(new SQLException("Query execution failed"));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails()).containsKey("message");
        assertThat(health.getDetails().get("error")).isEqualTo("SQLException");
        
        verify(statement).close();
        verify(connection).close();
    }
    
    @Test
    void shouldReturnDownWhenQueryReturnsUnexpectedResult() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(2); // Unexpected result
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails()).containsKey("message");
        assertThat(health.getDetails().get("error")).isEqualTo("QueryValidationFailed");
        
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }
    
    @Test
    void shouldReturnDownWhenResultSetIsEmpty() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false); // No results
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails()).containsKey("message");
        assertThat(health.getDetails().get("error")).isEqualTo("QueryValidationFailed");
        
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }
    
    @Test
    void shouldCloseResourcesEvenWhenExceptionOccurs() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenThrow(new SQLException("Test exception"));
        
        // When
        healthIndicator.health();
        
        // Then
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }
    
    @Test
    void shouldHandleMetaDataRetrievalFailure() throws Exception {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);
        when(connection.getMetaData()).thenThrow(new SQLException("Metadata error"));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        
        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }
}
