# TAAS - Telemetry as a Service

A production-ready telemetry ingestion system for temperature measurements from IoT devices. The system implements CQRS (Command Query Responsibility Segregation) and Hexagonal Architecture patterns to handle high-throughput streaming data with eventual consistency guarantees.

## Overview

This system solves the challenge of ingesting, processing, and querying temperature telemetry data from multiple devices while handling:
- **Duplicate submissions** (idempotency)
- **Out-of-order events** (late arrivals)
- **High throughput** (asynchronous processing)
- **Eventual consistency** (CQRS pattern)

## Prerequisites

- Java 21
- Docker and Docker Compose
- Gradle (or use the included wrapper)

## Quick Start

### 1. Start Infrastructure

Start PostgreSQL, Zookeeper, and Kafka using Docker Compose:

```bash
./start.sh
```

Or manually:

```bash
docker compose up -d
```

This will start:
- PostgreSQL on `localhost:5432`
- Kafka on `localhost:9092`
- Zookeeper on `localhost:2181`

The database schema will be automatically initialized from `src/main/resources/schema.sql`.

### 2. Run the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`.

### 3. Stop Infrastructure

```bash
./stop.sh
```

Or manually:

```bash
docker compose down
```

## Features

- ✅ **Idempotent telemetry ingestion** - Duplicate submissions handled gracefully
- ✅ **Out-of-order event handling** - Projection always reflects latest by timestamp
- ✅ **CQRS pattern** - Separate read/write models for optimal performance
- ✅ **Event-driven architecture** - Kafka for asynchronous processing
- ✅ **Hexagonal architecture** - Clean separation of concerns
- ✅ **Comprehensive error handling** - Meaningful error messages and status codes
- ✅ **Database-level duplicate prevention** - Unique constraint on (device_id, date)
- ✅ **Manual offset commit** - At-least-once processing guarantee
- ✅ **Partition by device** - Ordering guarantee per device in Kafka
- ✅ **Comprehensive test coverage** - Unit and integration tests with TestContainers

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.7**
- **PostgreSQL 16** (persistence)
- **Apache Kafka** (event streaming)
- **Gradle** (build tool)

## Architecture

### Design Principles

- **CQRS**: Separate write (command) and read (query) models for optimal performance
- **Hexagonal Architecture**: Decoupled business logic from infrastructure concerns
- **Event-Driven**: Kafka for asynchronous communication between command and query sides
- **Eventual Consistency**: Accept trade-off for better scalability and resilience
- **Idempotency**: Handle duplicate submissions gracefully at the database level

### Architecture Diagram

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP POST /api/v1/telemetry
       ▼
┌─────────────────────────────────────────────────────────┐
│                    REST API Layer                       │
│  ┌────────────────────────────────────────────────┐     │
│  │         TelemetryController                    │     │
│  └────────────────┬───────────────────────────────┘     │
└───────────────────┼─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│              Application Layer (CQRS)                   │
│  ┌──────────────────────┐  ┌──────────────────────┐     │
│  │ RecordTelemetry      │  │ GetDevices           │     │
│  │ CommandHandler       │  │ QueryHandler         │     │
│  └──────────┬───────────┘  └──────────┬───────────┘     │
└─────────────┼─────────────────────────┼────────────────┘
              │                         │
              ▼                         ▼
┌─────────────────────────┐   ┌──────────────────────┐
│   Domain Layer          │   │   Domain Layer       │
│  ┌──────────────────┐   │   │  ┌────────────────┐  │
│  │ Telemetry        │   │   │  │DeviceProjection│  │
│  │ TelemetryRecorded│   │   │  └────────────────┘  │
│  └──────────────────┘   │   └──────────────────────┘
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────┐
│           Infrastructure Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ PostgreSQL   │  │    Kafka     │  │  Kafka       │   │
│  │ (Write DB)   │  │  (Producer)  │  │ (Consumer)   │   │
│  └──────────────┘  └──────┬───────┘  └──────┬───────┘   │
└────────────────────────────┼─────────────────┼──────────┘
                             │                 │
                             ▼                 ▼
                    ┌─────────────────┐  ┌──────────────┐
                    │ Kafka Topic     │  │ PostgreSQL   │
                    │telemetry.record │  │ (Read DB)    │
                    └─────────────────┘  └──────────────┘
```

### Data Flow

**Write Path (Command Side):**
1. Client sends POST request with telemetry data
2. TelemetryController validates and creates RecordTelemetryCommand
3. RecordTelemetryCommandHandler processes the command:
   - Validates telemetry data (non-null checks, date not in future)
   - Checks for duplicates using `(deviceId, date)` as idempotency key
   - Persists to PostgreSQL `telemetry` table
   - Publishes `TelemetryRecorded` event to Kafka
4. Returns 202 Accepted to client

**Read Path (Query Side):**
1. TelemetryEventConsumer listens to Kafka topic `telemetry.recorded`
2. Receives TelemetryRecorded event
3. Retrieves existing DeviceProjection for the device
4. Compares event timestamp with current latest timestamp
5. Updates `device_projection` table only if event is newer
6. Logs warning for out-of-order events (older timestamps)
7. Commits Kafka offset after successful processing

**Query Path:**
1. Client sends GET request to `/api/v1/devices`
2. DeviceController creates GetDevicesQuery
3. GetDevicesQueryHandler queries `device_projection` table
4. Returns list of devices with their latest temperature

## API Endpoints

### 1. Record Telemetry (Command)

Submit temperature measurement from a device.

**Endpoint:** `POST /api/v1/telemetry`

**Request Body:**
```json
{
  "deviceId": 1,
  "measurement": 10.5,
  "date": "2025-01-31T13:00:00Z"
}
```

**Field Descriptions:**
- `deviceId` (Long, required): Unique identifier for the device
- `measurement` (BigDecimal, required): Temperature measurement value
- `date` (ISO 8601 timestamp, required): Timestamp of the measurement (cannot be in the future)

**Success Response:**
- **Code:** 202 Accepted
- **Body:** Empty

**Error Responses:**

| Status Code | Scenario | Response Body |
|-------------|----------|---------------|
| 400 Bad Request | Invalid data (null fields, future date) | `{"status": 400, "message": "Validation error details"}` |
| 503 Service Unavailable | Kafka or database unavailable | `{"status": 503, "message": "Service temporarily unavailable"}` |

**cURL Examples:**

```bash
# Submit telemetry for device 1
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": 1,
    "measurement": 23.5,
    "date": "2025-01-31T10:00:00Z"
  }'

# Submit telemetry for device 2
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": 2,
    "measurement": 18.2,
    "date": "2025-01-31T10:05:00Z"
  }'

# Submit duplicate (idempotent - will be handled gracefully)
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": 1,
    "measurement": 23.5,
    "date": "2025-01-31T10:00:00Z"
  }'

# Submit out-of-order telemetry (will be stored but won't update projection)
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": 1,
    "measurement": 22.0,
    "date": "2025-01-31T09:55:00Z"
  }'

# Invalid request - future date (will return 400)
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": 1,
    "measurement": 25.0,
    "date": "2099-01-31T10:00:00Z"
  }'

# Invalid request - missing deviceId (will return 400)
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{
    "measurement": 25.0,
    "date": "2025-01-31T10:00:00Z"
  }'
```

### 2. Get Devices (Query)

Retrieve all devices with their latest temperature measurement.

**Endpoint:** `GET /api/v1/devices`

**Success Response:**
- **Code:** 200 OK
- **Body:**
```json
[
  {
    "deviceId": 1,
    "measurement": 23.5,
    "date": "2025-01-31T10:00:00Z"
  },
  {
    "deviceId": 2,
    "measurement": 18.2,
    "date": "2025-01-31T10:05:00Z"
  }
]
```

**Field Descriptions:**
- `deviceId` (Long): Unique identifier for the device
- `measurement` (BigDecimal): Latest temperature measurement
- `date` (ISO 8601 timestamp): Timestamp of the latest measurement

**Error Responses:**

| Status Code | Scenario | Response Body |
|-------------|----------|---------------|
| 503 Service Unavailable | Database unavailable | `{"status": 503, "message": "Service temporarily unavailable"}` |

**cURL Examples:**

```bash
# Get all devices with latest temperature
curl http://localhost:8080/api/v1/devices

# Get all devices with formatted output (using jq)
curl http://localhost:8080/api/v1/devices | jq '.'

# Filter for specific device (using jq)
curl http://localhost:8080/api/v1/devices | jq '.[] | select(.deviceId == 1)'

# Count total devices
curl http://localhost:8080/api/v1/devices | jq 'length'
```

## Project Structure

The project follows **Hexagonal Architecture** with clear layer separation:

```
src/main/java/com/koni/telemetry/
├── domain/              # Business logic (entities, value objects, interfaces)
├── application/         # Use cases (command/query handlers, consumers)
└── infrastructure/      # Adapters (REST API, Kafka, PostgreSQL)
```

**Key Components:**
- **Domain**: [`Telemetry`](src/main/java/com/koni/telemetry/domain/model/Telemetry.java), [`DeviceProjection`](src/main/java/com/koni/telemetry/domain/model/DeviceProjection.java), [`TelemetryRecorded`](src/main/java/com/koni/telemetry/domain/event/TelemetryRecorded.java)
- **Application**: [`RecordTelemetryCommandHandler`](src/main/java/com/koni/telemetry/application/command/RecordTelemetryCommandHandler.java), [`GetDevicesQueryHandler`](src/main/java/com/koni/telemetry/application/query/GetDevicesQueryHandler.java), [`TelemetryEventConsumer`](src/main/java/com/koni/telemetry/application/consumer/TelemetryEventConsumer.java)
- **Infrastructure**: [`TelemetryController`](src/main/java/com/koni/telemetry/infrastructure/web/controller/TelemetryController.java), [`KafkaEventPublisher`](src/main/java/com/koni/telemetry/infrastructure/messaging/KafkaEventPublisher.java), JPA Repositories

## Database Schema

See [`src/main/resources/schema.sql`](src/main/resources/schema.sql) for complete schema.

### Telemetry Table (Write Model)
- Stores all telemetry records for audit
- Unique constraint on `(device_id, date)` ensures idempotency
- Indexed for fast duplicate checks

### Device Projection Table (Read Model)
- One row per device with latest measurement
- Updated asynchronously by Kafka consumer
- Optimized for fast reads

## Edge Case Handling

This system is designed to handle several challenging edge cases that commonly occur in distributed telemetry systems:

### 1. Duplicate Telemetry (Idempotency)

**Scenario:** The same telemetry data is submitted multiple times (same deviceId, measurement, and timestamp).

**Handling:**
- Database-level unique constraint on `(device_id, date)` prevents duplicate records
- First submission: Persisted to database and event published to Kafka
- Subsequent submissions: Detected as duplicate, no database write, no event published
- Both requests return success (200/202) to maintain idempotency
- No duplicate events reach the query side

**Example:**
```bash
# First submission - creates record
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 23.5, "date": "2025-01-31T10:00:00Z"}'
# Response: 202 Accepted

# Second submission - duplicate detected
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 23.5, "date": "2025-01-31T10:00:00Z"}'
# Response: 200 OK (already processed)
```

**Verification:**
```bash
# Check telemetry table - only one record exists
# Check device_projection - shows single measurement
curl http://localhost:8080/api/v1/devices | jq '.[] | select(.deviceId == 1)'
```

### 2. Out-of-Order Telemetry

**Scenario:** Telemetry arrives with an older timestamp than the current latest for that device.

**Handling:**
- All telemetry is persisted to the `telemetry` table for audit purposes
- Query side compares event timestamp with current latest timestamp
- If event is older: Projection is NOT updated, warning is logged
- If event is newer: Projection is updated with new measurement and timestamp
- Ensures projection always reflects the truly latest measurement by timestamp

**Example:**
```bash
# Submit T1 at 10:00
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 20.0, "date": "2025-01-31T10:00:00Z"}'

# Submit T2 at 10:05 (newer)
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 25.0, "date": "2025-01-31T10:05:00Z"}'

# Submit T3 at 10:02 (out-of-order - older than T2)
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 22.0, "date": "2025-01-31T10:02:00Z"}'

# Query devices - projection shows T2 (latest by timestamp)
curl http://localhost:8080/api/v1/devices | jq '.[] | select(.deviceId == 1)'
# Output: {"deviceId": 1, "measurement": 25.0, "date": "2025-01-31T10:05:00Z"}
```

**Verification:**
- All three records exist in `telemetry` table
- `device_projection` table shows T2 (10:05) as latest
- Application logs contain warning for T3 being out-of-order

### 3. Validation Errors

**Scenario:** Invalid data is submitted (null fields, future dates, invalid format).

**Handling:**
- Request validation occurs at REST controller level
- Invalid requests return 400 Bad Request with error details
- No database write or event publishing occurs
- Clear error messages guide the client

**Examples:**
```bash
# Future date - rejected
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 25.0, "date": "2099-01-31T10:00:00Z"}'
# Response: 400 Bad Request - "date cannot be in the future"

# Missing deviceId - rejected
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"measurement": 25.0, "date": "2025-01-31T10:00:00Z"}'
# Response: 400 Bad Request - "deviceId is required"

# Null measurement - rejected
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "date": "2025-01-31T10:00:00Z"}'
# Response: 400 Bad Request - "measurement is required"
```

### 4. Service Unavailability

**Scenario:** Kafka or PostgreSQL becomes temporarily unavailable.

**Handling:**
- Kafka unavailable: Retry up to 3 times, then return 503 Service Unavailable
- Database unavailable: Return 503 Service Unavailable immediately
- Clients can retry with exponential backoff
- System recovers automatically when services are restored

**Example:**
```bash
# If Kafka is down
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 25.0, "date": "2025-01-31T10:00:00Z"}'
# Response: 503 Service Unavailable - "Service temporarily unavailable"
```

### 5. Eventual Consistency

**Scenario:** Query side may not immediately reflect the latest write.

**Handling:**
- CQRS pattern introduces eventual consistency by design
- Write side returns 202 Accepted immediately after persisting
- Query side updates asynchronously via Kafka consumer
- Typical delay: milliseconds to seconds depending on load
- Trade-off: Better write performance and scalability

**Example:**
```bash
# Submit telemetry
curl -X POST http://localhost:8080/api/v1/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": 1, "measurement": 30.0, "date": "2025-01-31T11:00:00Z"}'
# Response: 202 Accepted (write completed)

# Immediately query - may not see new value yet
curl http://localhost:8080/api/v1/devices | jq '.[] | select(.deviceId == 1)'

# Wait a moment and query again - will see updated value
sleep 1
curl http://localhost:8080/api/v1/devices | jq '.[] | select(.deviceId == 1)'
# Output: {"deviceId": 1, "measurement": 30.0, "date": "2025-01-31T11:00:00Z"}
```

## Development

### Build

```bash
./gradlew build
```

### Run Tests

```bash
# Run all tests
./gradlew test

# Run only unit tests
./gradlew test --tests "*Test"

# Run only integration tests
./gradlew test --tests "*IntegrationTest"

# Run specific test class
./gradlew test --tests "RecordTelemetryCommandHandlerTest"

# Run tests with detailed output
./gradlew test --info
```

### Clean Build

```bash
./gradlew clean build
```

### Code Coverage

```bash
# Generate test coverage report
./gradlew test jacocoTestReport

# View report at: build/reports/jacoco/test/html/index.html
```

## Testing Strategy

The system includes comprehensive test coverage across multiple levels:

### Unit Tests

Focus on business logic in isolation with mocked dependencies:

- **RecordTelemetryCommandHandlerTest**: Command handler validation, duplicate detection, event publishing
- **TelemetryEventConsumerTest**: Projection updates, out-of-order handling, new device creation
- **GetDevicesQueryHandlerTest**: Query handler logic, DTO mapping
- **KafkaEventPublisherTest**: Event publishing, retry logic

### Integration Tests

Test complete flows with real infrastructure using TestContainers:

- **TelemetryControllerIntegrationTest**: REST API endpoints, validation, error handling
- **DeviceControllerIntegrationTest**: Query endpoint, empty results
- **KafkaEndToEndIntegrationTest**: Complete flow from POST → Kafka → Consumer → Projection
- **DuplicateTelemetryIntegrationTest**: Idempotency verification across all layers
- **OutOfOrderTelemetryIntegrationTest**: Out-of-order event handling verification
- **TelemetryRepositoryIdempotencyTest**: Database-level duplicate prevention

### Test Infrastructure

- **TestContainers**: Provides real PostgreSQL and Kafka instances for integration tests
- **Spring Boot Test**: Full application context for integration tests
- **Mockito**: Mocking framework for unit tests
- **JUnit 5**: Test framework

### Running Integration Tests

Integration tests automatically start PostgreSQL and Kafka containers:

```bash
# Ensure Docker is running
docker ps

# Run integration tests
./gradlew test --tests "*IntegrationTest"

# TestContainers will automatically:
# - Pull required Docker images (first run only)
# - Start PostgreSQL and Kafka containers
# - Run tests
# - Stop and remove containers
```

## Configuration

### Application Configuration

See [`src/main/resources/application.yaml`](src/main/resources/application.yaml) for complete configuration including:
- PostgreSQL datasource settings
- Kafka producer/consumer configuration (idempotence, retries, manual offset commit)
- Server port (8080)

### Environment Variables

Override configuration using environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/telemetry_db
export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export SERVER_PORT=8080
```

### Docker Compose

See [`docker-compose.yml`](docker-compose.yml) for infrastructure setup:
- PostgreSQL (port 5432)
- Kafka (port 9092)
- Zookeeper (port 2181)

## Troubleshooting

### Application won't start

**Problem:** Application fails to start with connection errors.

**Solution:**
```bash
# Check if Docker containers are running
docker ps

# If not running, start infrastructure
./start.sh

# Check container logs
docker compose logs postgres
docker compose logs kafka

# Verify PostgreSQL is ready
docker exec -it $(docker ps -q -f name=postgres) psql -U postgres -d telemetry_db -c "SELECT 1;"

# Verify Kafka is ready
docker exec -it $(docker ps -q -f name=kafka) kafka-topics --list --bootstrap-server localhost:9092
```

### Kafka connection issues

**Problem:** Application logs show Kafka connection errors.

**Solution:**
```bash
# Restart Kafka and Zookeeper
docker compose restart zookeeper kafka

# Wait for Kafka to be fully ready (can take 30-60 seconds)
sleep 30

# Verify Kafka topic exists
docker exec -it $(docker ps -q -f name=kafka) kafka-topics --describe --topic telemetry.recorded --bootstrap-server localhost:9092

# Restart application
./gradlew bootRun
```

### Database connection issues

**Problem:** Application logs show database connection errors.

**Solution:**
```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Restart PostgreSQL
docker compose restart postgres

# Verify database exists
docker exec -it $(docker ps -q -f name=postgres) psql -U postgres -l

# Check if schema is initialized
docker exec -it $(docker ps -q -f name=postgres) psql -U postgres -d telemetry_db -c "\dt"
```

### Projection not updating

**Problem:** POST requests succeed but GET doesn't show latest data.

**Solution:**
```bash
# Check Kafka consumer is running (look for consumer group in logs)
# Check application logs for consumer errors or out-of-order warnings

# Verify events are in Kafka topic
docker exec -it $(docker ps -q -f name=kafka) kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic telemetry.recorded \
  --from-beginning

# Check consumer lag
docker exec -it $(docker ps -q -f name=kafka) kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group telemetry-consumer-group

# If consumer is stuck, restart application to reset consumer
```

### Port already in use

**Problem:** Application fails to start with "Port 8080 already in use".

**Solution:**
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or use a different port
export SERVER_PORT=8081
./gradlew bootRun
```

### Tests failing

**Problem:** Integration tests fail with container startup errors.

**Solution:**
```bash
# Ensure Docker is running
docker ps

# Clean up old TestContainers
docker ps -a | grep testcontainers | awk '{print $1}' | xargs docker rm -f

# Ensure sufficient Docker resources (4GB+ RAM recommended)
# Run tests with more verbose output
./gradlew test --info
```

## Performance Considerations

### Throughput

- **Write throughput**: Limited by PostgreSQL write performance and Kafka producer throughput
- **Read throughput**: Optimized by read-only projection table with indexed queries
- **Kafka partitioning**: Using deviceId as partition key ensures ordering per device while allowing parallel processing

### Scalability

**Horizontal Scaling:**
- Application is stateless - run multiple instances
- Kafka consumer groups ensure partition distribution
- Load balancer distributes HTTP requests

**Vertical Scaling:**
- Increase Kafka partitions for higher throughput
- Add PostgreSQL read replicas for query side
- Tune connection pool and Kafka batch settings (see [`application.yaml`](src/main/resources/application.yaml))

### Latency

- **Write latency**: ~10-50ms (includes database write + Kafka publish)
- **Read latency**: ~5-20ms (simple indexed query)
- **End-to-end latency**: ~50-200ms (write → Kafka → consumer → projection update)

### Monitoring

**Key Metrics to Monitor:**
- Kafka consumer lag (should be near zero)
- Database connection pool utilization
- HTTP request latency (p50, p95, p99)
- Error rates (4xx, 5xx)
- Kafka producer send rate and failure rate

**Recommended Tools:**
- Spring Boot Actuator for application metrics
- Prometheus + Grafana for monitoring
- Kafka Manager for Kafka cluster monitoring
- PostgreSQL pg_stat_statements for query performance

## Security Considerations

### Current Implementation

This is a development/demo implementation. For production, consider:

**Authentication & Authorization:**
- Add Spring Security for API authentication
- Use OAuth2/JWT for token-based auth
- Implement role-based access control (RBAC)

**Network Security:**
- Use TLS/SSL for all connections (HTTPS, Kafka SSL, PostgreSQL SSL)
- Restrict database and Kafka access to application network
- Use secrets management (Vault, AWS Secrets Manager)

**Input Validation:**
- ✅ Already implemented: Request validation with Bean Validation
- ✅ Already implemented: SQL injection prevention via JPA
- Consider: Rate limiting to prevent abuse

**Data Privacy:**
- Consider encryption at rest for sensitive data
- Implement audit logging for compliance
- Add data retention policies

## Implementation Time

**Total Development Time: ~14 hours**

### Breakdown by Phase:

| Phase | Tasks | Time Spent |
|-------|-------|------------|
| **Setup & Infrastructure** | Project setup, Docker Compose, database schema | 2 hours |
| **Domain Layer** | Domain models, value objects, repository interfaces | 1.5 hours |
| **Application Layer** | Command/query handlers, event consumer | 2.5 hours |
| **Infrastructure Layer** | REST controllers, Kafka config, JPA repositories | 2 hours |
| **Error Handling** | Exception hierarchy, global exception handler | 1 hour |
| **Unit Tests** | Command handler, query handler, consumer tests | 2 hours |
| **Integration Tests** | End-to-end tests, edge case tests with TestContainers | 3 hours |
| **Documentation** | README, API docs, architecture diagrams | 1 hour |

### Key Learnings:

1. **CQRS Pattern**: Separating read and write models significantly improved code clarity and testability
2. **TestContainers**: Invaluable for integration testing with real infrastructure
3. **Idempotency**: Database-level unique constraints are simpler and more reliable than application-level checks
4. **Out-of-Order Events**: Timestamp comparison in the consumer is straightforward but requires careful testing
5. **Kafka Partitioning**: Using deviceId as partition key ensures ordering while maintaining scalability

## Future Enhancements

Potential improvements for production deployment:

- [ ] Add Spring Boot Actuator for health checks and metrics
- [ ] Implement distributed tracing (Zipkin/Jaeger)
- [ ] Add caching layer (Redis) for frequently accessed projections
- [ ] Implement API versioning
- [ ] Add rate limiting and throttling
- [ ] Implement dead letter queue for failed events
- [ ] Add support for multiple event types
- [ ] Implement event replay capability
- [ ] Add GraphQL API alongside REST
- [ ] Implement WebSocket for real-time updates
- [ ] Add Kubernetes deployment manifests
- [ ] Implement circuit breaker pattern (Resilience4j)
- [ ] Add comprehensive monitoring dashboards

## License

This project is an implementation of TAAS (Telemetry as a Service).

## Contact

For questions or issues, please contact the development team.

---

**Built with ❤️ using Spring Boot, Kafka, and PostgreSQL**
