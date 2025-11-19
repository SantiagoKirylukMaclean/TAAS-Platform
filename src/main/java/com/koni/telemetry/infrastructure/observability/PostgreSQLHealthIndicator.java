package com.koni.telemetry.infrastructure.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Health indicator for PostgreSQL database connectivity.
 * 
 * Tests database availability by executing a simple query.
 * Returns UP if database is reachable, DOWN if connection fails.
 * 
 * This health check is used for:
 * - Kubernetes readiness probes
 * - Monitoring dashboards
 * - Overall system health assessment
 * 
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgreSQLHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    /**
     * Checks PostgreSQL health by executing a simple query.
     * 
     * The check:
     * 1. Obtains a connection from the DataSource
     * 2. Executes "SELECT 1" to verify database connectivity
     * 3. Returns UP if successful, DOWN if any exception occurs
     * 
     * @return Health status with database details or error information
     */
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            
            if (resultSet.next() && resultSet.getInt(1) == 1) {
                String databaseProductName = connection.getMetaData().getDatabaseProductName();
                String databaseProductVersion = connection.getMetaData().getDatabaseProductVersion();
                
                log.debug("PostgreSQL health check passed: product={}, version={}", 
                    databaseProductName, databaseProductVersion);
                
                return Health.up()
                        .withDetail("database", databaseProductName)
                        .withDetail("version", databaseProductVersion)
                        .build();
            } else {
                log.error("PostgreSQL health check failed: query did not return expected result");
                
                return Health.down()
                        .withDetail("error", "QueryValidationFailed")
                        .withDetail("message", "SELECT 1 did not return expected result")
                        .build();
            }
            
        } catch (Exception e) {
            log.error("PostgreSQL health check failed", e);
            
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }
}
