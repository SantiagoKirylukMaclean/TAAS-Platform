#!/bin/bash

echo "Starting Telemetry Challenge infrastructure..."
echo ""

# Start Docker Compose services
docker-compose up -d

echo ""
echo "Waiting for services to be healthy..."
echo ""

# Wait for PostgreSQL
echo "Waiting for PostgreSQL..."
until docker exec telemetry-postgres pg_isready -U postgres > /dev/null 2>&1; do
  sleep 1
done
echo "✓ PostgreSQL is ready"

# Wait for Kafka
echo "Waiting for Kafka..."
sleep 10
echo "✓ Kafka is ready"

echo ""
echo "Infrastructure is ready!"
echo ""
echo "Services:"
echo "  - PostgreSQL: localhost:5432"
echo "  - Kafka: localhost:9092"
echo "  - Zookeeper: localhost:2181"
echo ""
echo "You can now start the Spring Boot application with:"
echo "  ./gradlew bootRun"
echo ""
