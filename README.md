# Telemetry Challenge

A telemetry ingestion system for temperature measurements from devices, implementing CQRS and Hexagonal Architecture patterns.

## Architecture

This system uses:
- **CQRS**: Separate write (command) and read (query) models
- **Hexagonal Architecture**: Decoupled business logic from infrastructure
- **Event-Driven**: Kafka for asynchronous communication between command and query sides
- **Eventual Consistency**: Optimized for scalability

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.7**
- **PostgreSQL 16** (persistence)
- **Apache Kafka** (event streaming)
- **Gradle** (build tool)

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

## API Endpoints

### Record Telemetry (Command)

```bash
POST /api/telemetry
Content-Type: application/json

{
  "deviceId": 1,
  "measurement": 10.5,
  "date": "2025-01-31T13:00:00Z"
}
```

Response: `202 Accepted`

### Get Devices (Query)

```bash
GET /api/devices
```

Response:
```json
[
  {
    "deviceId": 1,
    "measurement": 12.0,
    "date": "2025-01-31T13:00:05Z"
  }
]
```

## Project Structure

```
src/
├── main/
│   ├── java/com/koni/telemetry/
│   │   ├── domain/           # Domain models and interfaces
│   │   ├── application/      # Command/Query handlers
│   │   └── infrastructure/   # REST API, Kafka, JPA
│   └── resources/
│       ├── application.yaml  # Configuration
│       └── schema.sql        # Database schema
└── test/
    └── java/com/koni/telemetry/
```

## Configuration

Key configuration in `application.yaml`:

- **Database**: PostgreSQL connection settings
- **Kafka**: Producer/consumer configuration
- **Server**: Port 8080

## Database Schema

### Telemetry Table (Write Model)
Stores all received telemetry with idempotency constraint on `(device_id, date)`.

### Device Projection Table (Read Model)
Maintains the latest temperature per device for fast queries.

## Development

### Build

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Clean Build

```bash
./gradlew clean build
```

## Features

- ✅ Idempotent telemetry ingestion
- ✅ Out-of-order event handling
- ✅ CQRS with separate read/write models
- ✅ Event-driven architecture with Kafka
- ✅ Comprehensive error handling
- ✅ Database-level duplicate prevention

## Next Steps