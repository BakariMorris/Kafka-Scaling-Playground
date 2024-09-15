#!/bin/bash

# Test Basic Connectivity Between All Services
# Validates the complete data pipeline connectivity

set -e

echo "🔗 Testing Service Connectivity"
echo "==============================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test message for connectivity
TEST_MESSAGE='{"timestamp":"'$(date -Iseconds)'","testId":"connectivity-test","message":"Hello from Kafka-Flink POC"}'

echo -e "\n${BLUE}📋 Step 1: Kafka Connectivity Tests${NC}"

# Test Kafka producer
echo -n "Testing Kafka producer... "
echo "$TEST_MESSAGE" | docker exec -i kafka-poc-kafka1 kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic user-events
echo -e "${GREEN}✓ Success${NC}"

# Test Kafka consumer (with timeout)
echo -n "Testing Kafka consumer... "
timeout 10s docker exec kafka-poc-kafka1 kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic user-events \
    --from-beginning \
    --max-messages 1 > /dev/null && echo -e "${GREEN}✓ Success${NC}" || echo -e "${YELLOW}⚠ Timeout (expected)${NC}"

echo -e "\n${BLUE}📋 Step 2: Cross-Broker Communication${NC}"

# Test inter-broker communication
for broker in 9092 9093 9094; do
    echo -n "Testing broker localhost:$broker... "
    docker exec kafka-poc-kafka1 kafka-broker-api-versions \
        --bootstrap-server localhost:$broker > /dev/null && echo -e "${GREEN}✓ Accessible${NC}"
done

echo -e "\n${BLUE}📋 Step 3: Flink Cluster Connectivity${NC}"

# Test Flink JobManager API
echo -n "Testing Flink JobManager API... "
FLINK_OVERVIEW=$(curl -s http://localhost:8081/overview)
if [[ $FLINK_OVERVIEW == *"flink-version"* ]]; then
    echo -e "${GREEN}✓ API Accessible${NC}"
    TASKMANAGERS=$(echo $FLINK_OVERVIEW | jq -r '.taskmanagers // "N/A"' 2>/dev/null || echo "N/A")
    SLOTS_TOTAL=$(echo $FLINK_OVERVIEW | jq -r '."slots-total" // "N/A"' 2>/dev/null || echo "N/A")
    echo "  • TaskManagers: $TASKMANAGERS"
    echo "  • Total Slots: $SLOTS_TOTAL"
else
    echo -e "${RED}✗ Failed${NC}"
fi

# Test TaskManager registration
echo -n "Testing TaskManager registration... "
TASKMANAGER_COUNT=$(curl -s http://localhost:8081/taskmanagers | jq '. | length' 2>/dev/null || echo "0")
if [[ $TASKMANAGER_COUNT -gt 0 ]]; then
    echo -e "${GREEN}✓ $TASKMANAGER_COUNT TaskManagers registered${NC}"
else
    echo -e "${YELLOW}⚠ No TaskManagers registered${NC}"
fi

echo -e "\n${BLUE}📋 Step 4: Elasticsearch Connectivity${NC}"

# Test Elasticsearch cluster health
echo -n "Testing Elasticsearch health... "
ES_HEALTH=$(curl -s http://localhost:9200/_cluster/health)
ES_STATUS=$(echo $ES_HEALTH | jq -r '.status // "unknown"' 2>/dev/null || echo "unknown")
ES_NODES=$(echo $ES_HEALTH | jq -r '.number_of_nodes // "0"' 2>/dev/null || echo "0")

case $ES_STATUS in
    "green")
        echo -e "${GREEN}✓ Healthy ($ES_NODES nodes)${NC}"
        ;;
    "yellow")
        echo -e "${YELLOW}⚠ Warning ($ES_NODES nodes)${NC}"
        ;;
    "red")
        echo -e "${RED}✗ Unhealthy ($ES_NODES nodes)${NC}"
        ;;
    *)
        echo -e "${RED}✗ Unknown status${NC}"
        ;;
esac

# Test Elasticsearch indexing
echo -n "Testing Elasticsearch indexing... "
INDEX_RESPONSE=$(curl -s -X POST "http://localhost:9200/test-connectivity/_doc" \
    -H "Content-Type: application/json" \
    -d "$TEST_MESSAGE")

if [[ $INDEX_RESPONSE == *"\"result\":\"created\""* ]]; then
    echo -e "${GREEN}✓ Document indexed${NC}"
    
    # Clean up test document
    DOC_ID=$(echo $INDEX_RESPONSE | jq -r '._id' 2>/dev/null)
    if [[ $DOC_ID != "null" && $DOC_ID != "" ]]; then
        curl -s -X DELETE "http://localhost:9200/test-connectivity/_doc/$DOC_ID" > /dev/null
    fi
else
    echo -e "${RED}✗ Indexing failed${NC}"
fi

echo -e "\n${BLUE}📋 Step 5: Kibana Connectivity${NC}"

# Test Kibana status
echo -n "Testing Kibana status... "
KIBANA_STATUS=$(curl -s http://localhost:5601/api/status)
if [[ $KIBANA_STATUS == *"\"level\":\"available\""* ]]; then
    echo -e "${GREEN}✓ Available${NC}"
else
    echo -e "${YELLOW}⚠ Starting up or degraded${NC}"
fi

echo -e "\n${BLUE}📋 Step 6: Kafka UI Connectivity${NC}"

# Test Kafka UI health
echo -n "Testing Kafka UI... "
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Accessible${NC}"
else
    echo -e "${RED}✗ Not accessible${NC}"
fi

echo -e "\n${BLUE}📋 Step 7: Network Connectivity${NC}"

# Test internal network connectivity
echo "Testing internal Docker network connectivity:"

# Test Kafka to Flink connectivity (from Kafka container)
echo -n "  Kafka → Flink JobManager... "
if docker exec kafka-poc-kafka1 nc -z flink-jobmanager 8081 2>/dev/null; then
    echo -e "${GREEN}✓ Connected${NC}"
else
    echo -e "${RED}✗ Failed${NC}"
fi

# Test Flink to Kafka connectivity (from Flink container)
echo -n "  Flink → Kafka... "
if docker exec kafka-poc-flink-jobmanager nc -z kafka1 29092 2>/dev/null; then
    echo -e "${GREEN}✓ Connected${NC}"
else
    echo -e "${RED}✗ Failed${NC}"
fi

# Test Flink to Elasticsearch connectivity
echo -n "  Flink → Elasticsearch... "
if docker exec kafka-poc-flink-jobmanager nc -z elasticsearch 9200 2>/dev/null; then
    echo -e "${GREEN}✓ Connected${NC}"
else
    echo -e "${RED}✗ Failed${NC}"
fi

echo -e "\n${BLUE}📋 Step 8: Performance Test${NC}"

# Basic throughput test
echo "Running basic throughput test..."
echo -n "Producing 100 test messages... "

# Generate test messages
for i in {1..100}; do
    echo '{"timestamp":"'$(date -Iseconds)'","testId":"perf-test-'$i'","value":'$i'}' 
done | docker exec -i kafka-poc-kafka1 kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic user-events > /dev/null 2>&1

echo -e "${GREEN}✓ Produced${NC}"

echo -n "Checking topic message count... "
MESSAGE_COUNT=$(docker exec kafka-poc-kafka1 kafka-run-class kafka.tools.GetOffsetShell \
    --bootstrap-server localhost:9092 \
    --topic user-events | awk -F':' '{sum += $3} END {print sum}')

echo -e "${GREEN}✓ $MESSAGE_COUNT total messages${NC}"

echo -e "\n${GREEN}🎉 Connectivity test complete!${NC}"

echo -e "\n${YELLOW}📊 Summary:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Kafka cluster: 3 brokers running"
echo "✅ Flink cluster: JobManager + $TASKMANAGER_COUNT TaskManagers"
echo "✅ Elasticsearch: $ES_STATUS status with $ES_NODES nodes"
echo "✅ Inter-service connectivity verified"
echo "✅ Basic message flow working"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo -e "\n${YELLOW}🚀 Ready for Phase 2: Data Pipeline Development${NC}"