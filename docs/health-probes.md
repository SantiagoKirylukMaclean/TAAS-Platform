# Health Probes Configuration

## Overview

The telemetry service now exposes Kubernetes-compatible liveness and readiness probes for container orchestration.

## Endpoints

### Main Health Endpoint
```bash
curl http://localhost:8080/actuator/health
```

Returns overall health status including all components (database, Kafka, circuit breakers, etc.)

### Liveness Probe
```bash
curl http://localhost:8080/actuator/health/liveness
```

Indicates whether the application is running. Kubernetes uses this to determine if a container should be restarted.

**Response when healthy:**
```json
{
  "status": "UP"
}
```

### Readiness Probe
```bash
curl http://localhost:8080/actuator/health/readiness
```

Indicates whether the application is ready to accept traffic. Kubernetes uses this to determine if traffic should be routed to the container.

**Response when ready:**
```json
{
  "status": "UP"
}
```

## Kubernetes Configuration Example

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: telemetry-service
spec:
  containers:
  - name: telemetry
    image: telemetry-service:latest
    ports:
    - containerPort: 8080
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
      initialDelaySeconds: 30
      periodSeconds: 10
      timeoutSeconds: 5
      failureThreshold: 3
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 10
      periodSeconds: 5
      timeoutSeconds: 3
      failureThreshold: 3
```

## Configuration

The probes are configured in `application.yaml`:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

## Requirements

- **Requirement 11.4**: Liveness probe endpoint for Kubernetes
- **Requirement 11.5**: Readiness probe endpoint for Kubernetes

## Testing

Run the health probes configuration test:

```bash
./gradlew test --tests "com.koni.telemetry.infrastructure.observability.HealthProbesConfigurationTest"
```

All tests should pass, verifying that:
- Health endpoint bean is properly configured
- Liveness probe endpoint is accessible
- Readiness probe endpoint is accessible
- Main health endpoint includes component details
