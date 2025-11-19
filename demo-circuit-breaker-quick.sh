#!/bin/bash

echo "🛡️  Circuit Breaker Quick Demo"
echo "=============================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}Este demo muestra el circuit breaker en acción${NC}"
echo ""

echo "1️⃣  Estado inicial del circuit breaker:"
curl -s http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka | {state, failureRate, bufferedCalls, failedCalls}'
echo ""

echo "2️⃣  Deteniendo Kafka..."
KAFKA_CONTAINER=$(docker ps -q -f name=kafka)
docker stop $KAFKA_CONTAINER > /dev/null
echo -e "${GREEN}✓ Kafka detenido${NC}"
echo ""

echo "3️⃣  Enviando solicitudes (esto tomará ~2 minutos debido al timeout de 10s por solicitud)..."
echo "    Abre otra terminal y ejecuta este comando para ver el estado en tiempo real:"
echo -e "    ${YELLOW}watch -n 1 'curl -s http://localhost:8080/actuator/circuitbreakers | jq \".circuitBreakers.kafka.state\"'${NC}"
echo ""

for i in {1..12}; do
    echo -n "Solicitud $i/12: "
    START=$(date +%s)
    
    HTTP_CODE=$(curl -s -w "%{http_code}" -o /dev/null -X POST http://localhost:8080/api/v1/telemetry \
      -H "Content-Type: application/json" \
      -d "{\"deviceId\": 1, \"measurement\": 25.5, \"date\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}")
    
    END=$(date +%s)
    DURATION=$((END - START))
    
    # Check circuit breaker state after each request
    CB_STATE=$(curl -s http://localhost:8080/actuator/circuitbreakers | jq -r '.circuitBreakers.kafka.state')
    FAILED_CALLS=$(curl -s http://localhost:8080/actuator/circuitbreakers | jq -r '.circuitBreakers.kafka.failedCalls')
    
    if [ "$CB_STATE" = "OPEN" ]; then
        echo -e "${RED}FALLO${NC} (${DURATION}s) - ${RED}⚠️  CIRCUIT BREAKER ABIERTO!${NC} (fallos: $FAILED_CALLS)"
        echo ""
        echo -e "${GREEN}✓ Circuit breaker se abrió después de $i solicitudes${NC}"
        break
    elif [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        echo -e "${GREEN}OK${NC} (${DURATION}s) - Estado: $CB_STATE (fallos: $FAILED_CALLS)"
    else
        echo -e "${RED}FALLO${NC} (${DURATION}s) - Estado: $CB_STATE (fallos: $FAILED_CALLS)"
    fi
done

echo ""
echo "4️⃣  Estado actual del circuit breaker:"
curl -s http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka | {state, failureRate, bufferedCalls, failedCalls, notPermittedCalls}'
echo ""

echo "5️⃣  Verificando eventos en fallback:"
echo "    Conecta a PostgreSQL y ejecuta: SELECT COUNT(*) FROM fallback_events;"
echo ""

echo "6️⃣  Reiniciando Kafka..."
docker start $KAFKA_CONTAINER > /dev/null
echo -e "${GREEN}✓ Kafka reiniciado${NC}"
echo ""

echo "7️⃣  Esperando transición a HALF_OPEN (10 segundos)..."
for i in {10..1}; do
    echo -n "$i "
    sleep 1
done
echo ""
echo ""

echo "8️⃣  Estado después de esperar:"
curl -s http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka | {state, failureRate}'
echo ""

echo "9️⃣  Enviando solicitudes de prueba para cerrar el circuito..."
for i in {1..3}; do
    echo -n "Solicitud $i/3: "
    HTTP_CODE=$(curl -s -w "%{http_code}" -o /dev/null -X POST http://localhost:8080/api/v1/telemetry \
      -H "Content-Type: application/json" \
      -d "{\"deviceId\": 1, \"measurement\": 25.5, \"date\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}")
    
    CB_STATE=$(curl -s http://localhost:8080/actuator/circuitbreakers | jq -r '.circuitBreakers.kafka.state')
    
    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        echo -e "${GREEN}OK${NC} - Estado: $CB_STATE"
    else
        echo -e "${RED}FALLO${NC} - Estado: $CB_STATE"
    fi
    sleep 1
done
echo ""

echo "🔟 Estado final del circuit breaker:"
curl -s http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.kafka | {state, failureRate, bufferedCalls, failedCalls}'
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Demo completado!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Para reproducir los eventos almacenados en fallback:"
echo "  curl -X POST http://localhost:8080/api/v1/admin/fallback/replay"
echo ""
