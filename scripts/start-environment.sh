#!/bin/bash

# Start Complete Kafka-Flink POC Environment
# Orchestrates the startup of all services in correct order

set -e

echo "🚀 Starting Kafka-Flink POC Environment"
echo "======================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to wait for service
wait_for_service() {
    local service_name=$1
    local check_command=$2
    local max_attempts=60
    local attempt=1
    
    echo -n "Waiting for $service_name to be ready... "
    
    while [ $attempt -le $max_attempts ]; do
        if eval $check_command > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Ready${NC}"
            return 0
        fi
        sleep 3
        attempt=$((attempt + 1))
        echo -n "."
    done
    
    echo -e "${RED}✗ Timeout${NC}"
    return 1
}

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

echo -e "\n${BLUE}📋 Step 1: Starting Infrastructure Services${NC}"

# Start Zookeeper first
echo "Starting Zookeeper..."
docker-compose up -d zookeeper
wait_for_service "Zookeeper" "echo ruok | nc localhost 2181"

# Start Kafka brokers
echo -e "\nStarting Kafka cluster..."
docker-compose up -d kafka1 kafka2 kafka3
wait_for_service "Kafka Broker 1" "docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server localhost:9092 --list"
wait_for_service "Kafka Broker 2" "docker exec kafka-poc-kafka2 kafka-topics --bootstrap-server localhost:9093 --list"
wait_for_service "Kafka Broker 3" "docker exec kafka-poc-kafka3 kafka-topics --bootstrap-server localhost:9094 --list"

echo -e "\n${BLUE}📋 Step 2: Starting Processing Services${NC}"

# Start Flink cluster
echo "Starting Flink cluster..."
docker-compose up -d flink-jobmanager
wait_for_service "Flink JobManager" "curl -s http://localhost:8081/overview"

docker-compose up -d flink-taskmanager-1 flink-taskmanager-2
sleep 10 # Give TaskManagers time to register

echo -e "\n${BLUE}📋 Step 3: Starting Analytics Services${NC}"

# Start Elasticsearch
echo "Starting Elasticsearch..."
docker-compose up -d elasticsearch
wait_for_service "Elasticsearch" "curl -s http://localhost:9200/_cluster/health"

# Start Kibana
echo "Starting Kibana..."
docker-compose up -d kibana
wait_for_service "Kibana" "curl -s http://localhost:5601/api/status"

echo -e "\n${BLUE}📋 Step 4: Starting Management UIs${NC}"

# Start Kafka UI
echo "Starting Kafka UI..."
docker-compose up -d kafka-ui
wait_for_service "Kafka UI" "curl -s http://localhost:8080/actuator/health"

echo -e "\n${BLUE}📋 Step 5: Setting Up Topics${NC}"

# Create Kafka topics
echo "Creating Kafka topics..."
chmod +x scripts/setup-topics.sh
./scripts/setup-topics.sh

echo -e "\n${BLUE}📋 Step 6: Running Health Check${NC}"

# Run health check
chmod +x scripts/health-check.sh
./scripts/health-check.sh

echo -e "\n${GREEN}🎉 Environment startup complete!${NC}"

echo -e "\n${YELLOW}📖 Quick Access URLs:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🖥️  Flink Dashboard:    http://localhost:8081"
echo "📊 Kafka UI:           http://localhost:8080"
echo "📈 Kibana:             http://localhost:5601" 
echo "🔍 Elasticsearch:      http://localhost:9200"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo -e "\n${YELLOW}🛠️  Next Steps:${NC}"
echo "1. Check Flink cluster status: curl http://localhost:8081/overview"
echo "2. View Kafka topics: docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server localhost:9092 --list"
echo "3. Test message production: scripts/test-producer.sh"
echo "4. Deploy Flink jobs: flink run -c YourJobClass your-job.jar"

echo -e "\n${YELLOW}🔧 Management Commands:${NC}"
echo "• Stop all services:    docker-compose down"
echo "• View logs:           docker-compose logs -f [service_name]"
echo "• Restart service:     docker-compose restart [service_name]"
echo "• Scale TaskManagers:  docker-compose up -d --scale flink-taskmanager-1=3"