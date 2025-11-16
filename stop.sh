#!/bin/bash

echo "Stopping Telemetry Challenge infrastructure..."
echo ""

# Stop Docker Compose services
docker-compose down

echo ""
echo "Infrastructure stopped."
echo ""
