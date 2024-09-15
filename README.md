# Kafka-Flink POC - Real-Time Streaming Data Visualization

A comprehensive proof-of-concept demonstrating Apache Kafka and Apache Flink integration with real-time data visualization capabilities.

## Quick Start

### Prerequisites
- Docker and Docker Compose
- At least 8GB RAM available for containers
- Ports 2181, 8080, 8081, 9092-9094, 9200, 5601 available

### 1. Start the Environment
```bash
# Clone and navigate to project
git clone <repository-url>
cd Kafka-POC

# Start all services
./scripts/start-environment.sh
```

### 2. Verify Installation
```bash
# Run health check
./scripts/health-check.sh

# Test connectivity
./scripts/test-connectivity.sh
```

### 3. Access Services
- **Flink Dashboard**: http://localhost:8081
- **Kafka UI**: http://localhost:8080
- **Kibana**: http://localhost:5601
- **Elasticsearch**: http://localhost:9200

## Architecture Overview

```
Data Producers → Kafka (3 brokers) → Flink Processing → Elasticsearch → Kibana
                    ↓
               Real-time Dashboard (React)
```

## Current Status: Phase 1 Complete ✅

**Infrastructure Ready:**
- ✅ Kafka cluster (3 brokers)
- ✅ Zookeeper ensemble
- ✅ Flink cluster (JobManager + 2 TaskManagers)
- ✅ Elasticsearch single node
- ✅ Kibana for visualization
- ✅ Kafka UI for management
- ✅ All necessary topics created
- ✅ Health checks and connectivity tests

## Next Steps

**Phase 2: Data Pipeline Development**
- Create data producers for realistic streaming scenarios
- Implement basic Flink jobs for stream processing
- Set up Elasticsearch indexing pipeline

**Phase 3: Visualization Dashboard**
- Build React dashboard with real-time updates
- Implement WebSocket connections for live data
- Create interactive charts and metrics

**Phase 4: Advanced Features**
- Complex Event Processing (CEP) patterns
- Anomaly detection algorithms
- Failure injection and recovery demonstrations

## Commands Reference

### Environment Management
```bash
# Start all services
./scripts/start-environment.sh

# Stop all services
docker-compose down

# View service logs
docker-compose logs -f [service_name]

# Restart specific service
docker-compose restart [service_name]
```

### Kafka Operations
```bash
# List topics
docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server localhost:9092 --list

# Create topic
docker exec kafka-poc-kafka1 kafka-topics --bootstrap-server localhost:9092 --create --topic test-topic --partitions 3 --replication-factor 3

# Produce messages
docker exec -it kafka-poc-kafka1 kafka-console-producer --bootstrap-server localhost:9092 --topic user-events

# Consume messages
docker exec -it kafka-poc-kafka1 kafka-console-consumer --bootstrap-server localhost:9092 --topic user-events --from-beginning
```

### Flink Operations
```bash
# Access Flink CLI
docker exec -it kafka-poc-flink-jobmanager bash

# List running jobs
docker exec kafka-poc-flink-jobmanager flink list

# Submit job
docker exec kafka-poc-flink-jobmanager flink run /opt/flink/jobs/your-job.jar
```

### Elasticsearch Operations
```bash
# Check cluster health
curl http://localhost:9200/_cluster/health?pretty

# List indices
curl http://localhost:9200/_cat/indices?v

# Search documents
curl http://localhost:9200/your-index/_search?pretty
```

## Project Structure

```
Kafka-POC/
├── docker-compose.yml          # Main orchestration file
├── docker/                     # Service-specific configurations
├── scripts/                    # Management and setup scripts
├── flink-jobs/                 # Flink processing jobs (Phase 2)
├── data-producers/             # Data generation components (Phase 2)
├── dashboard/                  # React frontend (Phase 3)
└── monitoring/                 # Prometheus/Grafana setup (Phase 4)
```

## Troubleshooting

### Common Issues

**Services not starting:**
```bash
# Check Docker resources
docker system df
docker system prune

# Check port conflicts
netstat -tulpn | grep -E ":(2181|8080|8081|9092|9093|9094|9200|5601)"
```

**Kafka connection issues:**
```bash
# Verify broker connectivity
docker exec kafka-poc-kafka1 kafka-broker-api-versions --bootstrap-server localhost:9092
```

**Flink TaskManagers not registering:**
```bash
# Check TaskManager logs
docker-compose logs flink-taskmanager-1

# Verify JobManager is accessible
curl http://localhost:8081/overview
```

**Elasticsearch not responding:**
```bash
# Check Elasticsearch logs
docker-compose logs elasticsearch

# Verify cluster status
curl http://localhost:9200/_cluster/health
```

## Performance Tuning

### Resource Allocation
- **Kafka**: 1GB heap per broker
- **Flink JobManager**: 2GB heap
- **Flink TaskManager**: 4GB heap each
- **Elasticsearch**: 1GB heap

### Scaling
```bash
# Add more TaskManagers
docker-compose up -d --scale flink-taskmanager-1=3

# Monitor resource usage
docker stats
```

## Contributing

1. Follow the implementation phases in order
2. Run health checks after changes
3. Update documentation for new features
4. Test connectivity after modifications

## License

This project is for educational and demonstration purposes.