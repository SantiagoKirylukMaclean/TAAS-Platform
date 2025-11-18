#!/bin/bash

set -e

echo "=========================================="
echo "  Telemetry Challenge - Shutdown Script"
echo "=========================================="
echo ""

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
  echo "❌ Error: docker-compose is not installed."
  exit 1
fi

echo "Stopping infrastructure services..."
echo ""

# Stop Docker Compose services
docker-compose down

echo ""
echo "✓ Infrastructure stopped successfully"
echo ""
echo "To remove volumes (delete all data), run:"
echo "  docker-compose down -v"
echo ""
