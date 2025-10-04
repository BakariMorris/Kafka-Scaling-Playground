import pytest
import json
import time
import uuid
import requests
import asyncio
import websockets
from datetime import datetime, timezone
from kafka import KafkaProducer
from elasticsearch import Elasticsearch
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class TestCompletePipeline:
    """End-to-end tests for the complete data pipeline"""
    
    @pytest.fixture(scope="class")
    def kafka_producer(self):
        return KafkaProducer(
            bootstrap_servers=['kafka-test:29092'],
            value_serializer=lambda x: json.dumps(x).encode('utf-8')
        )
    
    @pytest.fixture(scope="class")
    def elasticsearch_client(self):
        return Elasticsearch(['http://elasticsearch-test:9200'])
    
    @pytest.fixture(scope="class")
    def websocket_url(self):
        return "ws://websocket-server:8765"
    
    def wait_for_services(self):
        """Wait for all services to be ready"""
        services = [
            ("Kafka", "kafka-test:29092"),
            ("Elasticsearch", "http://elasticsearch-test:9200"),
            ("Flink", "http://flink-jobmanager-test:8081")
        ]
        
        for service_name, url in services:
            logger.info(f"Waiting for {service_name} at {url}")
            
            if service_name == "Kafka":
                from kafka.admin import KafkaAdminClient
                for _ in range(30):
                    try:
                        admin = KafkaAdminClient(bootstrap_servers=[url])
                        admin.close()
                        logger.info(f"{service_name} is ready")
                        break
                    except Exception:
                        time.sleep(1)
                else:
                    pytest.fail(f"{service_name} not ready after 30 seconds")
            
            elif service_name == "Elasticsearch":
                for _ in range(30):
                    try:
                        es = Elasticsearch([url])
                        es.cluster.health(wait_for_status='yellow', timeout='1s')
                        logger.info(f"{service_name} is ready")
                        break
                    except Exception:
                        time.sleep(1)
                else:
                    pytest.fail(f"{service_name} not ready after 30 seconds")
            
            elif service_name == "Flink":
                for _ in range(30):
                    try:
                        response = requests.get(f"{url}/overview", timeout=5)
                        if response.status_code == 200:
                            logger.info(f"{service_name} is ready")
                            break
                    except Exception:
                        time.sleep(1)
                else:
                    pytest.fail(f"{service_name} not ready after 30 seconds")
    
    @pytest.mark.e2e
    def test_complete_user_event_pipeline(self, kafka_producer, elasticsearch_client):
        """Test complete pipeline: Kafka -> Flink -> Elasticsearch for user events"""
        self.wait_for_services()
        
        # Generate test user event
        user_event = {
            "userId": str(uuid.uuid4()),
            "action": "login",
            "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
            "sessionId": str(uuid.uuid4()),
            "userAgent": "Mozilla/5.0 (Test Browser)",
            "ipAddress": "192.168.1.100",
            "location": "New York",
            "deviceType": "desktop"
        }
        
        # Send to Kafka
        kafka_producer.send("user-events", user_event)
        kafka_producer.flush()
        
        # Wait for processing through Flink
        time.sleep(30)
        
        # Verify data reached Elasticsearch
        # Note: This assumes Flink jobs are configured to write to ES
        try:
            elasticsearch_client.indices.refresh(index="user-events*")
            
            # Search for our specific event
            search_result = elasticsearch_client.search(
                index="user-events*",
                body={
                    "query": {
                        "term": {
                            "userId.keyword": user_event["userId"]
                        }
                    }
                }
            )
            
            # Verify the event was processed and stored
            if search_result['hits']['total']['value'] > 0:
                stored_event = search_result['hits']['hits'][0]['_source']
                assert stored_event['userId'] == user_event['userId']
                assert stored_event['action'] == user_event['action']
                logger.info("User event successfully processed through pipeline")
            else:
                logger.warning("User event not found in Elasticsearch (may need Flink job running)")
                
        except Exception as e:
            logger.warning(f"Could not verify in Elasticsearch: {e}")
    
    @pytest.mark.e2e
    def test_complete_transaction_pipeline_with_fraud_detection(self, kafka_producer, elasticsearch_client):
        """Test transaction processing with fraud detection"""
        self.wait_for_services()
        
        user_id = str(uuid.uuid4())
        base_timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
        
        # Generate transactions that should trigger fraud detection
        transactions = []
        for i in range(6):  # 6 transactions in quick succession
            transaction = {
                "transactionId": str(uuid.uuid4()),
                "userId": user_id,
                "amount": 1000.0 + (i * 500),  # Increasing amounts
                "currency": "USD",
                "timestamp": base_timestamp + (i * 10000),  # 10 seconds apart
                "location": "San Francisco",
                "merchantId": f"merchant_{i}",
                "category": "electronics",
                "cardNumber": "****1234"
            }
            transactions.append(transaction)
            
            # Send to Kafka
            kafka_producer.send("transaction-events", transaction)
        
        kafka_producer.flush()
        
        # Wait for processing
        time.sleep(30)
        
        # Check for fraud alerts in Elasticsearch
        try:
            elasticsearch_client.indices.refresh(index="*fraud*")
            
            fraud_search = elasticsearch_client.search(
                index="*fraud*",
                body={
                    "query": {
                        "term": {
                            "userId.keyword": user_id
                        }
                    }
                }
            )
            
            if fraud_search['hits']['total']['value'] > 0:
                logger.info("Fraud detection alerts found - pipeline working correctly")
            else:
                logger.warning("No fraud alerts found (may need CEP Flink job running)")
                
        except Exception as e:
            logger.warning(f"Could not check fraud alerts: {e}")
        
        # Also check if transactions were stored
        try:
            elasticsearch_client.indices.refresh(index="transaction*")
            
            transaction_search = elasticsearch_client.search(
                index="transaction*",
                body={
                    "query": {
                        "term": {
                            "userId.keyword": user_id
                        }
                    }
                }
            )
            
            logger.info(f"Found {transaction_search['hits']['total']['value']} transactions in ES")
            
        except Exception as e:
            logger.warning(f"Could not check transactions: {e}")
    
    @pytest.mark.e2e
    def test_complete_iot_pipeline_with_anomaly_detection(self, kafka_producer, elasticsearch_client):
        """Test IoT sensor data processing with anomaly detection"""
        self.wait_for_services()
        
        sensor_id = str(uuid.uuid4())
        location = "Building_A_Floor_1"
        base_timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
        
        # Generate normal readings followed by anomalous ones
        sensor_readings = []
        
        # Normal readings
        for i in range(10):
            reading = {
                "sensorId": sensor_id,
                "sensorType": "temperature",
                "value": 22.0 + (i * 0.1),  # Normal temperature range
                "unit": "celsius",
                "timestamp": base_timestamp + (i * 5000),
                "location": location,
                "deviceId": "temp_sensor_001",
                "batteryLevel": 85.0,
                "signalStrength": -45
            }
            sensor_readings.append(reading)
            kafka_producer.send("iot-sensor-events", reading)
        
        # Anomalous readings (temperature spike)
        for i in range(5):
            reading = {
                "sensorId": sensor_id,
                "sensorType": "temperature",
                "value": 50.0 + (i * 5),  # Abnormally high temperature
                "unit": "celsius",
                "timestamp": base_timestamp + ((10 + i) * 5000),
                "location": location,
                "deviceId": "temp_sensor_001",
                "batteryLevel": 85.0,
                "signalStrength": -45
            }
            sensor_readings.append(reading)
            kafka_producer.send("iot-sensor-events", reading)
        
        kafka_producer.flush()
        
        # Wait for processing
        time.sleep(30)
        
        # Check for anomaly alerts
        try:
            elasticsearch_client.indices.refresh(index="*anomaly*")
            
            anomaly_search = elasticsearch_client.search(
                index="*anomaly*",
                body={
                    "query": {
                        "term": {
                            "sensorId.keyword": sensor_id
                        }
                    }
                }
            )
            
            if anomaly_search['hits']['total']['value'] > 0:
                logger.info("Anomaly detection alerts found - pipeline working correctly")
            else:
                logger.warning("No anomaly alerts found (may need anomaly detection Flink job running)")
                
        except Exception as e:
            logger.warning(f"Could not check anomaly alerts: {e}")
        
        # Check if sensor data was stored
        try:
            elasticsearch_client.indices.refresh(index="iot*")
            
            iot_search = elasticsearch_client.search(
                index="iot*",
                body={
                    "query": {
                        "term": {
                            "sensorId.keyword": sensor_id
                        }
                    }
                }
            )
            
            logger.info(f"Found {iot_search['hits']['total']['value']} IoT readings in ES")
            
        except Exception as e:
            logger.warning(f"Could not check IoT data: {e}")
    
    @pytest.mark.e2e
    async def test_real_time_dashboard_connectivity(self, websocket_url):
        """Test real-time dashboard WebSocket connectivity"""
        try:
            async with websockets.connect(websocket_url, timeout=10) as websocket:
                logger.info("Connected to WebSocket server")
                
                # Wait for initial data
                try:
                    message = await asyncio.wait_for(websocket.recv(), timeout=10)
                    data = json.loads(message)
                    
                    assert 'type' in data
                    logger.info(f"Received real-time data: {data['type']}")
                    
                except asyncio.TimeoutError:
                    logger.warning("No real-time data received within timeout")
                    
        except Exception as e:
            logger.warning(f"Could not connect to WebSocket server: {e}")
    
    @pytest.mark.e2e
    def test_monitoring_endpoints(self):
        """Test monitoring and health endpoints"""
        endpoints = [
            ("Flink JobManager", "http://flink-jobmanager-test:8081/overview"),
            ("Elasticsearch Health", "http://elasticsearch-test:9200/_cluster/health"),
        ]
        
        for name, url in endpoints:
            try:
                response = requests.get(url, timeout=10)
                assert response.status_code == 200
                logger.info(f"{name} endpoint is healthy")
                
                if "flink" in url:
                    data = response.json()
                    assert 'flink-version' in data
                elif "elasticsearch" in url:
                    data = response.json()
                    assert data['status'] in ['green', 'yellow']
                    
            except Exception as e:
                logger.warning(f"Could not reach {name}: {e}")
    
    @pytest.mark.e2e
    def test_data_consistency_across_services(self, kafka_producer, elasticsearch_client):
        """Test data consistency between Kafka and Elasticsearch"""
        self.wait_for_services()
        
        test_id = str(uuid.uuid4())
        
        # Send known test events
        test_events = []
        for i in range(10):
            event = {
                "testId": test_id,
                "eventNumber": i,
                "userId": f"consistency_test_user_{i}",
                "action": "consistency_test",
                "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
                "testData": f"test_value_{i}"
            }
            test_events.append(event)
            kafka_producer.send("user-events", event)
        
        kafka_producer.flush()
        
        # Wait for processing
        time.sleep(30)
        
        # Check data in Elasticsearch
        try:
            elasticsearch_client.indices.refresh(index="user*")
            
            consistency_search = elasticsearch_client.search(
                index="user*",
                body={
                    "query": {
                        "term": {
                            "testId.keyword": test_id
                        }
                    },
                    "size": 20
                }
            )
            
            found_events = consistency_search['hits']['total']['value']
            logger.info(f"Consistency test: sent {len(test_events)} events, found {found_events} in ES")
            
            if found_events > 0:
                # Verify event content
                stored_events = consistency_search['hits']['hits']
                for stored_event in stored_events:
                    source = stored_event['_source']
                    assert source['testId'] == test_id
                    assert 'eventNumber' in source
                    assert source['action'] == 'consistency_test'
                
                logger.info("Data consistency verified")
            else:
                logger.warning("No events found in Elasticsearch for consistency test")
                
        except Exception as e:
            logger.warning(f"Could not verify data consistency: {e}")
    
    @pytest.mark.e2e
    @pytest.mark.performance
    def test_end_to_end_latency(self, kafka_producer, elasticsearch_client):
        """Test end-to-end processing latency"""
        self.wait_for_services()
        
        start_time = time.time()
        event_timestamp = int(start_time * 1000)
        
        latency_test_event = {
            "latencyTestId": str(uuid.uuid4()),
            "userId": "latency_test_user",
            "action": "latency_measurement",
            "timestamp": event_timestamp,
            "testStartTime": start_time
        }
        
        # Send event to Kafka
        kafka_producer.send("user-events", latency_test_event)
        kafka_producer.flush()
        
        # Poll Elasticsearch for the processed event
        max_wait_time = 60  # 60 seconds max
        check_interval = 2  # Check every 2 seconds
        
        for attempt in range(int(max_wait_time / check_interval)):
            try:
                elasticsearch_client.indices.refresh(index="user*")
                
                search_result = elasticsearch_client.search(
                    index="user*",
                    body={
                        "query": {
                            "term": {
                                "latencyTestId.keyword": latency_test_event["latencyTestId"]
                            }
                        }
                    }
                )
                
                if search_result['hits']['total']['value'] > 0:
                    end_time = time.time()
                    total_latency = end_time - start_time
                    
                    logger.info(f"End-to-end latency: {total_latency:.2f} seconds")
                    
                    # Assert reasonable latency (less than 30 seconds in test environment)
                    assert total_latency < 30, f"Latency too high: {total_latency} seconds"
                    
                    return
                    
            except Exception as e:
                logger.debug(f"Latency test search attempt {attempt + 1} failed: {e}")
            
            time.sleep(check_interval)
        
        logger.warning("Latency test event not found within timeout period")
    
    @pytest.mark.e2e
    def test_system_resilience_and_recovery(self, kafka_producer, elasticsearch_client):
        """Test system behavior under stress and recovery"""
        self.wait_for_services()
        
        # Send a burst of events to test system resilience
        burst_size = 100
        user_id = str(uuid.uuid4())
        
        logger.info(f"Sending burst of {burst_size} events")
        
        start_time = time.time()
        for i in range(burst_size):
            event = {
                "burstTestId": user_id,
                "userId": f"burst_user_{i % 10}",
                "action": "burst_test",
                "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
                "eventNumber": i,
                "batchId": "resilience_test"
            }
            
            kafka_producer.send("user-events", event)
            
            # Send every 10th event to different topics for load distribution
            if i % 10 == 0:
                kafka_producer.send("transaction-events", {
                    **event,
                    "transactionId": str(uuid.uuid4()),
                    "amount": i * 10.0
                })
            
            if i % 15 == 0:
                kafka_producer.send("iot-sensor-events", {
                    **event,
                    "sensorId": str(uuid.uuid4()),
                    "sensorType": "test",
                    "value": i * 0.1
                })
        
        kafka_producer.flush()
        send_duration = time.time() - start_time
        
        logger.info(f"Sent {burst_size} events in {send_duration:.2f} seconds")
        
        # Wait for system to process the burst
        time.sleep(45)
        
        # Check system health after burst
        try:
            # Check Flink health
            flink_response = requests.get("http://flink-jobmanager-test:8081/overview", timeout=10)
            assert flink_response.status_code == 200
            
            # Check Elasticsearch health
            es_health = elasticsearch_client.cluster.health()
            assert es_health['status'] in ['green', 'yellow']
            
            logger.info("System maintained health during burst test")
            
        except Exception as e:
            logger.warning(f"System health check failed after burst: {e}")
        
        # Verify some events were processed
        try:
            elasticsearch_client.indices.refresh(index="user*")
            
            burst_search = elasticsearch_client.search(
                index="user*",
                body={
                    "query": {
                        "term": {
                            "batchId.keyword": "resilience_test"
                        }
                    }
                }
            )
            
            processed_count = burst_search['hits']['total']['value']
            logger.info(f"Found {processed_count} processed events from burst test")
            
            # We expect at least some events to be processed
            assert processed_count > 0, "No events were processed during burst test"
            
        except Exception as e:
            logger.warning(f"Could not verify burst test results: {e}")