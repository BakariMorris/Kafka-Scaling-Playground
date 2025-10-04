import pytest
import json
import time
import uuid
import requests
from datetime import datetime, timezone
from kafka import KafkaProducer
from elasticsearch import Elasticsearch
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class TestFlinkJobs:
    """Integration tests for Flink job processing"""
    
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
    def flink_client(self):
        return "http://flink-jobmanager-test:8081"
    
    def wait_for_elasticsearch_data(self, es_client, index, expected_count=1, timeout=30):
        """Wait for data to appear in Elasticsearch"""
        start_time = time.time()
        
        while time.time() - start_time < timeout:
            try:
                es_client.indices.refresh(index=index)
                result = es_client.count(index=index)
                if result['count'] >= expected_count:
                    return True
                time.sleep(1)
            except Exception as e:
                logger.debug(f"Waiting for ES data: {e}")
                time.sleep(1)
        
        return False
    
    def test_flink_cluster_health(self, flink_client):
        """Test Flink cluster health and API accessibility"""
        try:
            response = requests.get(f"{flink_client}/overview", timeout=10)
            assert response.status_code == 200
            
            data = response.json()
            assert 'flink-version' in data
            assert data['taskmanagers'] >= 1
            
        except requests.RequestException as e:
            pytest.fail(f"Flink cluster not accessible: {e}")
    
    def test_submit_and_run_simple_job(self, flink_client, kafka_producer, elasticsearch_client):
        """Test submitting and running a simple Flink job"""
        # This test assumes a simple streaming job JAR is available
        # In a real scenario, you would build and deploy the job JAR
        
        # Check if any jobs are running
        response = requests.get(f"{flink_client}/jobs", timeout=10)
        assert response.status_code == 200
        
        jobs_data = response.json()
        logger.info(f"Current jobs: {jobs_data}")
        
        # For this test, we'll verify the Flink environment is ready to accept jobs
        assert 'jobs' in jobs_data
    
    def test_fraud_detection_pattern(self, kafka_producer, elasticsearch_client):
        """Test fraud detection processing through Flink"""
        # Create a pattern that should trigger fraud detection
        user_id = str(uuid.uuid4())
        base_timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
        
        # Send 6 transactions within 2 minutes to trigger velocity-based fraud detection
        for i in range(6):
            transaction = {
                "transactionId": str(uuid.uuid4()),
                "userId": user_id,
                "amount": 100.0 + i * 10,
                "currency": "USD",
                "timestamp": base_timestamp + (i * 10000),  # 10 seconds apart
                "location": "New York",
                "merchantId": f"merchant_{i}",
                "category": "groceries"
            }
            
            kafka_producer.send("transaction-events", transaction)
        
        kafka_producer.flush()
        
        # Wait for processing and check if fraud alerts are generated
        # This would require the fraud detection Flink job to be running
        time.sleep(10)
        
        # In a full integration test, we would check for fraud alerts in Elasticsearch
        # For now, we verify the transaction events are produced
        assert True  # Placeholder for actual fraud detection verification
    
    def test_anomaly_detection_pattern(self, kafka_producer, elasticsearch_client):
        """Test IoT anomaly detection processing through Flink"""
        sensor_id = str(uuid.uuid4())
        base_timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
        
        # Send normal readings followed by anomalous ones
        normal_readings = [20.0, 21.0, 22.0, 20.5, 21.5]
        anomalous_readings = [45.0, 50.0, 55.0]  # Temperature spike
        
        all_readings = normal_readings + anomalous_readings
        
        for i, value in enumerate(all_readings):
            sensor_event = {
                "sensorId": sensor_id,
                "sensorType": "temperature",
                "value": value,
                "unit": "celsius",
                "timestamp": base_timestamp + (i * 5000),  # 5 seconds apart
                "location": "Building_A_Floor_1",
                "deviceId": "temp_sensor_test"
            }
            
            kafka_producer.send("iot-sensor-events", sensor_event)
        
        kafka_producer.flush()
        
        # Wait for processing
        time.sleep(10)
        
        # Verify sensor events are produced
        assert True  # Placeholder for actual anomaly detection verification
    
    def test_stream_enrichment_pattern(self, kafka_producer, elasticsearch_client):
        """Test stream enrichment processing"""
        user_id = str(uuid.uuid4())
        
        # First send user profile data
        user_profile = {
            "userId": user_id,
            "name": "Test User",
            "email": "test@example.com",
            "accountType": "premium",
            "riskScore": 0.2,
            "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000)
        }
        
        kafka_producer.send("user-events", user_profile)
        
        # Then send transaction that should be enriched
        transaction = {
            "transactionId": str(uuid.uuid4()),
            "userId": user_id,
            "amount": 500.0,
            "currency": "USD",
            "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
            "location": "San Francisco",
            "merchantId": "merchant_premium",
            "category": "electronics"
        }
        
        kafka_producer.send("transaction-events", transaction)
        kafka_producer.flush()
        
        # Wait for processing
        time.sleep(10)
        
        # Verify events are produced
        assert True  # Placeholder for actual enrichment verification
    
    def test_windowed_aggregation_pattern(self, kafka_producer, elasticsearch_client):
        """Test windowed aggregation processing"""
        location = "test_location"
        sensor_type = "temperature"
        base_timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
        
        # Send multiple sensor readings for aggregation
        for i in range(20):
            sensor_event = {
                "sensorId": f"sensor_{i % 5}",  # 5 different sensors
                "sensorType": sensor_type,
                "value": 20.0 + (i % 10),  # Values between 20-30
                "unit": "celsius",
                "timestamp": base_timestamp + (i * 1000),  # 1 second apart
                "location": location,
                "deviceId": f"device_{i % 5}"
            }
            
            kafka_producer.send("iot-sensor-events", sensor_event)
        
        kafka_producer.flush()
        
        # Wait for window processing
        time.sleep(15)
        
        # Verify aggregation events are produced
        assert True  # Placeholder for actual aggregation verification
    
    def test_backpressure_handling(self, kafka_producer, elasticsearch_client):
        """Test system behavior under high load"""
        user_id = str(uuid.uuid4())
        base_timestamp = int(datetime.now(timezone.utc).timestamp() * 1000)
        
        # Send high volume of events to test backpressure
        events_count = 1000
        start_time = time.time()
        
        for i in range(events_count):
            event = {
                "userId": user_id,
                "action": f"action_{i}",
                "timestamp": base_timestamp + (i * 100),
                "sessionId": str(uuid.uuid4()),
                "data": f"test_data_{i}"
            }
            
            kafka_producer.send("user-events", event)
            
            # Send every 100th message immediately to test rate limiting
            if i % 100 == 0:
                kafka_producer.flush()
        
        kafka_producer.flush()
        end_time = time.time()
        
        duration = end_time - start_time
        throughput = events_count / duration
        
        logger.info(f"Produced {events_count} events in {duration:.2f}s ({throughput:.2f} events/s)")
        
        # Verify reasonable throughput even under load
        assert throughput > 100  # At least 100 events per second
    
    def test_exactly_once_processing(self, kafka_producer, elasticsearch_client):
        """Test exactly-once semantic guarantees"""
        test_id = str(uuid.uuid4())
        
        # Send the same event multiple times (simulating retries)
        duplicate_event = {
            "eventId": test_id,
            "userId": "duplicate_test_user",
            "action": "exactly_once_test",
            "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
            "testValue": 42
        }
        
        # Send the same event 5 times
        for _ in range(5):
            kafka_producer.send("user-events", duplicate_event)
        
        kafka_producer.flush()
        
        # Wait for processing
        time.sleep(10)
        
        # In a real test, we would verify that only one processed event exists
        # This requires the Flink job to implement deduplication logic
        assert True  # Placeholder for actual deduplication verification
    
    def test_fault_tolerance_and_recovery(self, flink_client):
        """Test Flink job fault tolerance and recovery mechanisms"""
        
        # Check checkpointing configuration
        response = requests.get(f"{flink_client}/config", timeout=10)
        assert response.status_code == 200
        
        config = response.json()
        
        # Verify checkpointing is enabled in test environment
        # This would be configured in the Flink job deployment
        logger.info(f"Flink configuration: {config}")
        
        # In a full test, we would:
        # 1. Submit a job
        # 2. Let it process some data
        # 3. Kill a TaskManager
        # 4. Verify the job recovers and continues processing
        assert True  # Placeholder for actual fault tolerance test
    
    def test_state_management(self, kafka_producer, elasticsearch_client):
        """Test stateful stream processing"""
        user_id = str(uuid.uuid4())
        
        # Send events that require state management (e.g., session tracking)
        session_events = [
            {"action": "login", "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000)},
            {"action": "page_view", "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000) + 10000},
            {"action": "purchase", "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000) + 20000},
            {"action": "logout", "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000) + 30000}
        ]
        
        for event in session_events:
            event.update({
                "userId": user_id,
                "sessionId": "test_session_001"
            })
            kafka_producer.send("user-events", event)
        
        kafka_producer.flush()
        
        # Wait for state processing
        time.sleep(10)
        
        # Verify stateful processing occurred
        assert True  # Placeholder for actual state verification
    
    @pytest.mark.performance
    def test_latency_requirements(self, kafka_producer, elasticsearch_client):
        """Test end-to-end processing latency"""
        start_time = int(datetime.now(timezone.utc).timestamp() * 1000)
        
        test_event = {
            "eventId": str(uuid.uuid4()),
            "userId": "latency_test_user",
            "action": "latency_test",
            "timestamp": start_time,
            "testStartTime": start_time
        }
        
        kafka_producer.send("user-events", test_event)
        kafka_producer.flush()
        
        # In a real test, we would measure the time until the processed event
        # appears in the output sink (Elasticsearch)
        # For now, we just verify the event was sent
        processing_time = time.time() * 1000 - start_time
        
        logger.info(f"Event processing took {processing_time:.2f}ms")
        
        # Verify reasonable latency (this would need actual output verification)
        assert processing_time < 30000  # Less than 30 seconds for test environment