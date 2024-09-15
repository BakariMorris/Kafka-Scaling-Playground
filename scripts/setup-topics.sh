#!/bin/bash

# Kafka Topics Setup Script
# Creates all necessary topics for the Kafka-Flink POC

set -e

echo "📝 Creating Kafka Topics for POC"
echo "================================="

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Kafka broker endpoints
KAFKA_BROKERS="localhost:9092"

# Function to create topic
create_topic() {
    local topic_name=$1
    local partitions=$2
    local replication_factor=$3
    local cleanup_policy=${4:-"delete"}
    local retention_ms=${5:-"604800000"} # 7 days default
    
    echo -n "Creating topic '$topic_name'... "
    
    # Check if topic already exists
    if docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server $KAFKA_BROKERS --list | grep -q "^${topic_name}$"; then
        echo -e "${YELLOW}Already exists${NC}"
        return 0
    fi
    
    # Create the topic
    docker exec kafka-poc-kafka1 kafka-topics \
        --bootstrap-server $KAFKA_BROKERS \
        --create \
        --topic $topic_name \
        --partitions $partitions \
        --replication-factor $replication_factor \
        --config cleanup.policy=$cleanup_policy \
        --config retention.ms=$retention_ms \
        --config min.insync.replicas=2
    
    echo -e "${GREEN}✓ Created${NC}"
}

echo -e "\n${YELLOW}🚀 Raw Event Topics:${NC}"

# User events topic
create_topic "user-events" 6 3 "delete" "604800000"

# Transaction events topic  
create_topic "transaction-events" 6 3 "delete" "604800000"

# IoT sensor events topic
create_topic "iot-sensor-events" 6 3 "delete" "604800000"

# System metrics topic
create_topic "system-metrics" 6 3 "delete" "604800000"

echo -e "\n${YELLOW}⚡ Processed Event Topics:${NC}"

# Aggregated events topic
create_topic "aggregated-events" 6 3 "delete" "2592000000" # 30 days

# Enriched events topic
create_topic "enriched-events" 6 3 "delete" "604800000"

# Anomaly detection results
create_topic "anomaly-alerts" 3 3 "delete" "2592000000" # 30 days

# CEP pattern matches
create_topic "pattern-matches" 3 3 "delete" "2592000000" # 30 days

echo -e "\n${YELLOW}📊 Analytics Topics:${NC}"

# Real-time dashboards topic
create_topic "dashboard-metrics" 3 3 "delete" "86400000" # 1 day

# KPIs and aggregated metrics
create_topic "kpi-metrics" 3 3 "delete" "2592000000" # 30 days

echo -e "\n${YELLOW}🔧 System Topics:${NC}"

# Flink checkpointing coordination (compacted)
create_topic "flink-coordination" 1 3 "compact" "604800000"

# Job status and health monitoring
create_topic "job-status" 1 3 "compact" "604800000"

echo -e "\n${YELLOW}📋 Topic Summary:${NC}"
docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server $KAFKA_BROKERS --list | sort

echo -e "\n${YELLOW}📄 Topic Details:${NC}"
docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server $KAFKA_BROKERS --describe | grep -E "(Topic:|PartitionCount:|ReplicationFactor:)"

echo -e "\n${GREEN}✅ All topics created successfully!${NC}"

echo -e "\n${YELLOW}🧪 Test Commands:${NC}"
echo "# List all topics:"
echo "docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server $KAFKA_BROKERS --list"
echo ""
echo "# Produce test message:"  
echo "docker exec -it kafka-poc-kafka1 kafka-console-producer --bootstrap-server $KAFKA_BROKERS --topic user-events"
echo ""
echo "# Consume messages:"
echo "docker exec -it kafka-poc-kafka1 kafka-console-consumer --bootstrap-server $KAFKA_BROKERS --topic user-events --from-beginning"