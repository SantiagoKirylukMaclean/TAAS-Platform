#!/bin/bash

set -e

echo "=========================================="
echo "  Telemetry Challenge - Startup Script"
echo "=========================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "❌ Error: Docker is not running. Please start Docker and try again."
  exit 1
fi

echo "✓ Docker is running"
echo ""

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
  echo "❌ Error: docker-compose is not installed. Please install docker-compose and try again."
  exit 1
fi

echo "Starting infrastructure services..."
echo ""

# Start Docker Compose services
docker-compose up -d

echo ""
echo "Waiting for services to be healthy..."
echo ""

# Wait for PostgreSQL with timeout
echo "⏳ Waiting for PostgreSQL..."
POSTGRES_TIMEOUT=30
POSTGRES_ELAPSED=0
until docker exec telemetry-postgres pg_isready -U postgres > /dev/null 2>&1; do
  if [ $POSTGRES_ELAPSED -ge $POSTGRES_TIMEOUT ]; then
    echo "❌ PostgreSQL failed to start within ${POSTGRES_TIMEOUT} seconds"
    echo "   Check logs with: docker-compose logs postgres"
    exit 1
  fi
  sleep 1
  POSTGRES_ELAPSED=$((POSTGRES_ELAPSED + 1))
done
echo "✓ PostgreSQL is ready"

# Wait for Kafka with timeout
echo "⏳ Waiting for Kafka..."
KAFKA_TIMEOUT=30
KAFKA_ELAPSED=0
until docker exec telemetry-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; do
  if [ $KAFKA_ELAPSED -ge $KAFKA_TIMEOUT ]; then
    echo "❌ Kafka failed to start within ${KAFKA_TIMEOUT} seconds"
    echo "   Check logs with: docker-compose logs kafka"
    exit 1
  fi
  sleep 1
  KAFKA_ELAPSED=$((KAFKA_ELAPSED + 1))
done
echo "✓ Kafka is ready"

echo ""
echo "=========================================="
echo "  Infrastructure is ready!"
echo "=========================================="
echo ""
echo "Services:"
echo "  - PostgreSQL: localhost:5432"
echo "    Database: telemetry_db"
echo "    User: postgres"
echo ""
echo "  - Kafka: localhost:9092"
echo "    Topic: telemetry.recorded"
echo ""
echo "  - Zookeeper: localhost:2181"
echo ""
echo "Next steps:"
echo "  1. Start the application:"
echo "     ./gradlew bootRun"
echo ""
echo "  2. Test the API:"
echo "     curl -X POST http://localhost:8080/api/telemetry \\"
echo "       -H 'Content-Type: application/json' \\"
echo "       -d '{\"deviceId\":1,\"measurement\":25.5,\"date\":\"2025-01-31T10:00:00Z\"}'"
echo ""
echo "  3. View devices:"
echo "     curl http://localhost:8080/api/devices"
echo ""
echo "  4. Stop infrastructure:"
echo "     ./stop.sh"
echo ""
