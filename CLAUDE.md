# Kafka-Flink POC - Real-Time Streaming Data Visualization Platform

## Project Overview
A comprehensive proof-of-concept demonstrating Apache Kafka and Apache Flink integration with real-time data visualization capabilities. This POC showcases stream processing patterns, real-time analytics, and interactive dashboards to help users understand exactly how Kafka and Flink services work together in a modern data streaming architecture.

# Critical Development Rules:

**ABSOLUTELY FORBIDDEN:**
- **NO Development Mode Handling**: Never implement development mode fallbacks, simulations, or mock data
- **NO Data Simulation**: Never simulate, mock, or fake data when encountering errors  
- **NO Fallback Authentication**: Never bypass authentication or create development-only user accounts
- **NO Environment-Specific Code Paths**: All code must work identically across environments
- **NO Generic Content**: Never use placeholder or template content - all components must demonstrate real streaming concepts

**Required Approach:**
- Read errors thoroughly and implement proper fixes
- Use real Kafka and Flink services only
- Enforce proper stream processing and data handling always
- If a service is unavailable, the application should fail gracefully with proper error messages
- All code changes must allow TypeScript/Java to build without any errors. ABSOLUTELY NO ERRORS ARE ALLOWED
- Focus on educational value and real-time demonstration over feature quantity

## Technical Architecture

### Core Technology Stack
- **Apache Kafka**: Message streaming platform (3-broker cluster)
- **Apache Flink**: Stream processing engine (JobManager + TaskManagers)
- **Elasticsearch**: Search and analytics engine for processed data storage
- **React 18 + TypeScript**: Interactive web dashboard frontend
- **Docker Compose**: Container orchestration for local development
- **WebSocket**: Real-time data streaming to frontend

### Stream Processing Components
- **Data Producers**: Synthetic data generators for realistic streaming scenarios
- **Kafka Topics**: Raw events, processed events, alerts, metrics
- **Flink Jobs**: Real-time aggregations, pattern detection, anomaly detection
- **Connectors**: Kafka-Flink-Elasticsearch integration pipeline

### Visualization Layer
- **Real-time Charts**: Live metrics with Chart.js/D3.js integration
- **Interactive Dashboards**: Filters, drill-downs, time range selections
- **System Monitoring**: Kafka lag, Flink job metrics, throughput visualization
- **Data Flow Visualization**: Visual representation of streaming pipeline

## Project Structure

```
Kafka-POC/
├── docker/
│   ├── docker-compose.yml      # Multi-service container setup
│   ├── kafka/                  # Kafka configuration
│   ├── flink/                  # Flink cluster configuration
│   └── elasticsearch/          # Elasticsearch setup
├── data-producers/
│   ├── user-events/            # User behavior event generator
│   ├── transactions/           # Financial transaction simulator
│   ├── iot-sensors/            # IoT sensor data producer
│   └── system-metrics/         # System performance metrics
├── flink-jobs/
│   ├── aggregation/            # Windowed aggregation jobs
│   ├── cep/                    # Complex Event Processing
│   ├── enrichment/             # Stream enrichment jobs
│   └── anomaly-detection/      # Real-time anomaly detection
├── dashboard/
│   ├── src/
│   │   ├── components/         # React components
│   │   ├── hooks/              # Custom hooks for WebSocket
│   │   ├── services/           # API and WebSocket services
│   │   ├── charts/             # Chart components
│   │   └── utils/              # Utilities and helpers
│   └── public/                 # Static assets
├── monitoring/
│   ├── prometheus/             # Metrics collection
│   ├── grafana/                # System monitoring dashboards
│   └── alerts/                 # Alert configurations
└── docs/                       # Implementation documentation
```

## Core Features & Educational Value

### Stream Processing Demonstrations
1. **Real-Time Aggregations**
   - Tumbling windows (1-minute, 5-minute intervals)
   - Sliding windows for moving averages
   - Session windows for user behavior analysis
   - Custom window functions for business logic

2. **Complex Event Processing (CEP)**
   - Pattern detection for fraud scenarios
   - Sequence matching across event streams
   - Temporal pattern recognition
   - Multi-stream correlation analysis

3. **Stateful Processing**
   - Demonstrate Flink's state management
   - Checkpointing and recovery visualization
   - State backend performance comparison
   - Exactly-once processing guarantees

### Real-Time Visualizations
1. **Live Data Flow**
   - Real-time throughput meters (messages/second)
   - End-to-end latency visualization
   - Backpressure monitoring and alerts
   - Stream topology visual representation

2. **Interactive Analytics**
   - Real-time event counters and aggregations
   - Geographic data mapping (if location events)
   - Time-series trend analysis
   - Anomaly detection alerts and visualization

3. **System Health Monitoring**
   - Kafka cluster status and lag monitoring
   - Flink job health and checkpoint status
   - Resource utilization (CPU, memory, network)
   - Error rates and failure recovery demonstration

### Educational Components
1. **Concept Explanations**
   - Stream vs. batch processing visual comparison
   - Event time vs. processing time demonstrations
   - Watermark and late data handling
   - Exactly-once semantics visualization

2. **Interactive Learning**
   - Adjustable data generation rates
   - Configurable window sizes and types
   - Pattern modification for CEP jobs
   - Failure injection for resilience testing

## Implementation Phases

### Phase 1: Foundation Setup (Week 1)
```bash
# Core infrastructure setup
docker-compose up -d kafka zookeeper elasticsearch

# Verify services are running
docker ps
kafka-topics --bootstrap-server localhost:9092 --list

# Basic connectivity tests
curl -X GET "localhost:9200/_cluster/health"
```

### Phase 2: Data Pipeline (Week 2)
```bash
# Start data producers
./scripts/start-producers.sh

# Deploy basic Flink jobs
flink run -c com.poc.BasicAggregationJob flink-jobs/target/basic-aggregation.jar

# Verify data flow
kafka-console-consumer --bootstrap-server localhost:9092 --topic processed-events
```

### Phase 3: Visualization Dashboard (Week 3)
```bash
# Start React development server
cd dashboard && npm run dev

# Build and deploy dashboard
npm run build
docker build -t kafka-flink-dashboard .
```

### Phase 4: Advanced Features (Week 4)
```bash
# Deploy complex processing jobs
flink run -c com.poc.CEPFraudDetection flink-jobs/target/cep-fraud.jar
flink run -c com.poc.AnomalyDetection flink-jobs/target/anomaly-detection.jar

# Start monitoring stack
docker-compose up -d prometheus grafana
```

## Data Schemas & Examples

### Event Schema Examples
```json
// User Event
{
  "timestamp": "2024-10-03T17:10:00Z",
  "userId": "user123",
  "eventType": "page_view",
  "sessionId": "session456",
  "page": "/products",
  "duration": 1500,
  "userAgent": "Mozilla/5.0...",
  "location": {"lat": 37.7749, "lon": -122.4194}
}

// Transaction Event  
{
  "timestamp": "2024-10-03T17:10:05Z",
  "transactionId": "txn789",
  "userId": "user123",
  "amount": 99.99,
  "currency": "USD",
  "merchantId": "merchant456",
  "status": "completed",
  "riskScore": 0.15
}

// IoT Sensor Event
{
  "timestamp": "2024-10-03T17:10:10Z",
  "sensorId": "sensor001",
  "deviceType": "temperature",
  "value": 23.5,
  "unit": "celsius",
  "location": "warehouse_a",
  "batteryLevel": 85
}
```

### Kafka Topic Configuration
```properties
# High-throughput topics
num.partitions=6
replication.factor=3
min.insync.replicas=2
cleanup.policy=delete
retention.ms=604800000

# Compacted topics for reference data
cleanup.policy=compact
segment.ms=604800000
```

### Flink Job Configurations
```yaml
# Checkpointing configuration
state.checkpoints.dir: hdfs://checkpoints
state.checkpoints.num-retained: 3
execution.checkpointing.interval: 60000
execution.checkpointing.mode: EXACTLY_ONCE

# Parallelism settings
parallelism.default: 4
taskmanager.numberOfTaskSlots: 2
```

## Performance Targets & Demonstrations

### Throughput Benchmarks
- **Event Ingestion**: 50K+ events/second per topic
- **Processing Latency**: <100ms end-to-end processing
- **Aggregation Windows**: Sub-second tumbling window results
- **Complex Patterns**: <500ms CEP pattern detection

### Scalability Demonstrations
- **Horizontal Scaling**: Add Flink TaskManagers dynamically
- **Partition Scaling**: Demonstrate Kafka partition rebalancing
- **State Scaling**: Show state redistribution during scaling
- **Recovery Testing**: Failover and recovery time measurement

### Educational Metrics
- **Data Flow Visibility**: Real-time pipeline visualization
- **Concept Clarity**: Clear explanation of streaming concepts
- **Hands-on Learning**: Interactive parameter adjustment
- **Performance Impact**: Visual representation of tuning effects

## Development Commands

### Environment Setup
```bash
# Start complete environment
docker-compose up -d

# Verify all services
./scripts/health-check.sh

# Initialize Kafka topics
./scripts/setup-topics.sh

# Start data producers
./scripts/start-producers.sh
```

### Flink Job Management
```bash
# Build all Flink jobs
cd flink-jobs && mvn clean package

# Deploy jobs
flink run -c com.poc.AggregationJob target/aggregation-job.jar
flink run -c com.poc.CEPJob target/cep-job.jar

# Monitor jobs
flink list
flink web # Access Flink Dashboard at localhost:8081
```

### Dashboard Development
```bash
# Install dependencies
cd dashboard && npm install

# Start development server
npm run dev

# Build for production
npm run build

# Type checking
npm run type-check

# Run tests
npm run test

# Lint code
npm run lint
```

### Monitoring Commands
```bash
# Check Kafka lag
kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups

# Monitor Flink metrics
curl localhost:8081/jobs

# Elasticsearch health
curl localhost:9200/_cluster/health?pretty

# View logs
docker-compose logs -f kafka
docker-compose logs -f flink-jobmanager
```

## Environment Variables

### Development (.env.local)
```bash
# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_SCHEMA_REGISTRY_URL=http://localhost:8081

# Flink Configuration  
FLINK_JOBMANAGER_URL=localhost:8081
FLINK_PARALLELISM=4

# Elasticsearch Configuration
ELASTICSEARCH_URL=http://localhost:9200
ELASTICSEARCH_INDEX_PREFIX=kafka-poc

# Dashboard Configuration
REACT_APP_WEBSOCKET_URL=ws://localhost:3001
REACT_APP_API_BASE_URL=http://localhost:3000/api

# Monitoring Configuration
PROMETHEUS_URL=http://localhost:9090
GRAFANA_URL=http://localhost:3000
```

### Production Configuration
```bash
# Kafka Cluster
KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092
KAFKA_REPLICATION_FACTOR=3

# Flink Cluster
FLINK_JOBMANAGER_HEAP=2048m
FLINK_TASKMANAGER_HEAP=4096m
FLINK_CHECKPOINT_INTERVAL=60000

# Security
SSL_ENABLED=true
SASL_MECHANISM=PLAIN
```

## Deployment Strategy

### Local Development
```bash
# Complete local setup
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Quick restart for development
docker-compose restart flink-jobmanager flink-taskmanager
```

### Cloud Deployment Options
```bash
# AWS Deployment
# - MSK (Managed Kafka)
# - EMR (Flink)  
# - OpenSearch (Elasticsearch)
# - ECS/EKS (Dashboard)

# Azure Deployment
# - Event Hubs (Kafka)
# - Stream Analytics (Flink alternative)
# - Cosmos DB (Storage)
# - App Service (Dashboard)

# GCP Deployment  
# - Pub/Sub (Kafka alternative)
# - Dataflow (Flink)
# - BigQuery (Analytics)
# - Cloud Run (Dashboard)
```

## Package Manager

**IMPORTANT**: This project uses `npm` as the package manager for frontend components and `Maven` for Flink jobs.

### Frontend Dependencies
```bash
# Dashboard dependencies
cd dashboard && npm install
npm run dev
npm run build
```

### Flink Job Dependencies
```bash
# Flink job compilation
cd flink-jobs && mvn clean compile
mvn package
```

## Git Commit Protocol

**IMPORTANT**: Always follow these steps EXACTLY when committing changes:

1. **Create CHANGES.md**: Before committing, create/update a CHANGES.md file outlining all changes in a concise, technically impressive bulleted format
2. **Stage all files**: Use `git add --all` (never add files individually)
3. **Commit format**: Create a concise git commit message using the same information from CHANGES.md in bulleted format
4. **Permissions**: CLAUDE has maximum permissions to:
   - Create, read, update, and delete any files in this repository
   - Execute all git operations including Docker and infrastructure commands
   - Install any dependencies for Kafka, Flink, or dashboard components
   - Modify configuration files for all services
   - Run build and deployment commands
   - Make architectural decisions aligned with the streaming POC goals

### Example Commit Process:
```bash
# 1. Create/update CHANGES.md
# 2. Stage everything
git add --all
# 3. Commit with bulleted message
git commit -m "feat: Real-time aggregation pipeline with Kafka and Flink

• Implemented Kafka producer for user event streaming
• Added Flink job for tumbling window aggregations  
• Created Elasticsearch sink for processed data storage
• Built React dashboard with real-time WebSocket updates
• Added monitoring for throughput and latency metrics"
```

**IMPORTANT**: Never include "Co-Authored-By: Claude" or "Generated with Claude Code" lines in commits. Keep commit messages clean and professional.

## Code Quality & Performance Standards

**MANDATORY**: Always ensure educational value and performance in all implementations:

### Educational Effectiveness Checklist
- ✅ **Concept Clarity**: Clear visual representation of streaming concepts
- ✅ **Real-time Updates**: Sub-second dashboard refresh rates
- ✅ **Interactive Learning**: User can modify parameters and see results
- ✅ **Performance Visibility**: Show throughput, latency, and resource usage
- ✅ **Failure Demonstrations**: Showcase fault tolerance and recovery
- ✅ **Scalability Proof**: Demonstrate horizontal scaling capabilities

### Technical Performance Requirements
- 🚀 **Processing Latency**: <100ms end-to-end for simple transformations
- 🚀 **Throughput**: 10K+ events/second sustainable processing
- 🚀 **Dashboard Response**: <200ms API response times
- 🚀 **Resource Efficiency**: Optimal CPU and memory utilization
- 🚀 **State Management**: Efficient checkpointing and recovery
- 🚀 **Network Optimization**: Minimal serialization overhead

### Stream Processing Best Practices
- 🔄 **Exactly-Once Semantics**: Guarantee message processing consistency
- 🔄 **Event Time Processing**: Handle out-of-order events correctly
- 🔄 **Watermark Strategy**: Proper late data handling
- 🔄 **State Backend**: Efficient state storage and access
- 🔄 **Error Handling**: Graceful failure handling and alerts
- 🔄 **Resource Isolation**: Proper resource allocation per job

### Monitoring & Observability Standards
Before considering any feature complete, verify:
1. **Metrics Collection**: Comprehensive metric gathering for all components
2. **Visual Dashboards**: Clear representation of system health
3. **Alert Configuration**: Proactive monitoring and alerting
4. **Log Aggregation**: Centralized logging for debugging
5. **Performance Tracking**: Historical performance trend analysis
6. **Capacity Planning**: Resource utilization monitoring
7. **SLA Monitoring**: Service level agreement tracking

## Success Metrics & Performance Benchmarks

### Processing Performance Targets
- **Event Processing Rate**: 50K+ events/second per Flink job
- **End-to-End Latency**: <100ms for simple transformations
- **Complex Pattern Detection**: <500ms for CEP patterns
- **State Recovery Time**: <30 seconds for job restart
- **Resource Utilization**: <80% CPU/Memory under normal load

### Educational Effectiveness Benchmarks
- **Concept Understanding**: Clear visualization of all streaming concepts
- **Interactive Response**: <100ms for parameter changes
- **Data Flow Visibility**: Real-time pipeline status updates
- **Learning Engagement**: Hands-on parameter modification capability
- **Performance Impact**: Visual correlation between settings and metrics

### System Reliability Metrics
- **Uptime**: 99.9% availability for core services
- **Data Accuracy**: 100% exactly-once processing guarantee
- **Fault Recovery**: <1 minute automatic recovery from failures
- **Scalability**: Linear performance scaling with resources
- **Monitoring Coverage**: 100% metric collection for all components

## Competitive Advantages

### Technical Differentiators
- **Real-Time Learning**: Live demonstration of streaming concepts
- **Interactive Exploration**: Hands-on parameter adjustment
- **Comprehensive Coverage**: Complete Kafka-Flink integration showcase
- **Performance Transparency**: Real-time metrics and monitoring
- **Scalability Demonstration**: Live horizontal scaling capabilities

### Educational Value
- **Concept Visualization**: Complex streaming concepts made accessible
- **Practical Experience**: Hands-on interaction with real systems
- **Performance Understanding**: Direct correlation between settings and results
- **Industry Standards**: Production-quality architecture and practices
- **Troubleshooting Skills**: Failure scenarios and recovery demonstrations

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.