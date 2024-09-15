#!/bin/bash

# Kafka-Flink POC Health Check Script
# Verifies all services are running and accessible

set -e

echo "🔍 Kafka-Flink POC Health Check"
echo "================================"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if service is ready
check_service() {
    local service_name=$1
    local check_command=$2
    local max_attempts=30
    local attempt=1
    
    echo -n "Checking $service_name... "
    
    while [ $attempt -le $max_attempts ]; do
        if eval $check_command > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Ready${NC}"
            return 0
        fi
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo -e "${RED}✗ Failed${NC}"
    return 1
}

# Check Docker containers are running
echo -e "\n${YELLOW}📋 Docker Container Status:${NC}"
docker-compose ps

echo -e "\n${YELLOW}🔌 Service Health Checks:${NC}"

# Check Zookeeper
check_service "Zookeeper" "echo ruok | nc localhost 2181"

# Check Kafka brokers
check_service "Kafka Broker 1" "docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server localhost:9092 --list"
check_service "Kafka Broker 2" "docker exec kafka-poc-kafka2 kafka-topics --bootstrap-server localhost:9093 --list"  
check_service "Kafka Broker 3" "docker exec kafka-poc-kafka3 kafka-topics --bootstrap-server localhost:9094 --list"

# Check Flink JobManager
check_service "Flink JobManager" "curl -s http://localhost:8081/overview"

# Check Elasticsearch
check_service "Elasticsearch" "curl -s http://localhost:9200/_cluster/health"

# Check Kibana
check_service "Kibana" "curl -s http://localhost:5601/api/status"

# Check Kafka UI
check_service "Kafka UI" "curl -s http://localhost:8080/actuator/health"

echo -e "\n${YELLOW}📊 Detailed Service Information:${NC}"

# Kafka cluster info
echo -e "\n${GREEN}Kafka Cluster:${NC}"
docker exec kafka-poc-kafka1 kafka-broker-api-versions --bootstrap-server localhost:9092 | head -1

# Flink cluster info
echo -e "\n${GREEN}Flink Cluster:${NC}"
curl -s http://localhost:8081/overview | jq '.flink-version, .taskmanagers, .slots-total, .slots-available' 2>/dev/null || echo "Flink API accessible"

# Elasticsearch cluster info  
echo -e "\n${GREEN}Elasticsearch Cluster:${NC}"
curl -s http://localhost:9200/_cluster/health | jq '.status, .number_of_nodes' 2>/dev/null || echo "Elasticsearch API accessible"

echo -e "\n${GREEN}✅ Health check complete!${NC}"
echo -e "\n${YELLOW}📖 Access URLs:${NC}"
echo "• Flink Dashboard: http://localhost:8081"
echo "• Kafka UI: http://localhost:8080" 
echo "• Kibana: http://localhost:5601"
echo "• Elasticsearch: http://localhost:9200"