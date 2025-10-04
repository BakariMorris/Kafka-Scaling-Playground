import pytest
import json
import time
import uuid
from datetime import datetime, timezone
from kafka import KafkaProducer, KafkaConsumer
from kafka.admin import KafkaAdminClient, NewTopic
import requests
from elasticsearch import Elasticsearch
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class TestKafkaPipeline:
    """Integration tests for Kafka message pipeline"""
    
    @pytest.fixture(scope="class")
    def kafka_config(self):
        return {
            'bootstrap_servers': ['kafka-test:29092'],
            'value_serializer': lambda x: json.dumps(x).encode('utf-8'),
            'value_deserializer': lambda m: json.loads(m.value.decode('utf-8'))
        }
    
    @pytest.fixture(scope="class")
    def kafka_admin(self, kafka_config):
        return KafkaAdminClient(bootstrap_servers=kafka_config['bootstrap_servers'])
    
    @pytest.fixture(scope="class")
    def kafka_producer(self, kafka_config):
        return KafkaProducer(
            bootstrap_servers=kafka_config['bootstrap_servers'],
            value_serializer=kafka_config['value_serializer']
        )
    
    @pytest.fixture(scope="class")
    def elasticsearch_client(self):
        return Elasticsearch(['http://elasticsearch-test:9200'])
    
    @pytest.fixture(autouse=True)
    def setup_topics(self, kafka_admin):
        """Create test topics"""
        topics = [
            NewTopic(name="test-user-events", num_partitions=3, replication_factor=1),
            NewTopic(name="test-transaction-events", num_partitions=3, replication_factor=1),
            NewTopic(name="test-iot-sensor-events", num_partitions=3, replication_factor=1),
        ]
        
        try:
            kafka_admin.create_topics(topics)
            time.sleep(2)  # Wait for topics to be created
        except Exception as e:
            logger.info(f"Topics might already exist: {e}")
    
    def test_produce_and_consume_user_events(self, kafka_producer, kafka_config):
        """Test producing and consuming user events"""
        topic = "test-user-events"
        test_event = {
            "userId": str(uuid.uuid4()),
            "action": "login",
            "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
            "sessionId": str(uuid.uuid4()),
            "userAgent": "test-agent",
            "ipAddress": "192.168.1.100"
        }
        
        # Produce event
        future = kafka_producer.send(topic, test_event)
        result = future.get(timeout=10)
        
        assert result.topic == topic
        assert result.partition >= 0
        
        # Consume event
        consumer = KafkaConsumer(
            topic,
            bootstrap_servers=kafka_config['bootstrap_servers'],
            value_deserializer=kafka_config['value_deserializer'],
            auto_offset_reset='earliest',
            consumer_timeout_ms=10000
        )
        
        messages = []
        for message in consumer:
            messages.append(message.value)
            if len(messages) >= 1:
                break
        
        consumer.close()
        
        assert len(messages) == 1
        assert messages[0]['userId'] == test_event['userId']
        assert messages[0]['action'] == test_event['action']
    
    def test_produce_and_consume_transaction_events(self, kafka_producer, kafka_config):
        """Test producing and consuming transaction events"""
        topic = "test-transaction-events"
        test_event = {
            "transactionId": str(uuid.uuid4()),
            "userId": str(uuid.uuid4()),
            "amount": 150.75,
            "currency": "USD",
            "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
            "location": "New York",
            "merchantId": "merchant_123",
            "category": "groceries"
        }
        
        # Produce event
        kafka_producer.send(topic, test_event).get(timeout=10)
        
        # Consume event
        consumer = KafkaConsumer(
            topic,
            bootstrap_servers=kafka_config['bootstrap_servers'],
            value_deserializer=kafka_config['value_deserializer'],
            auto_offset_reset='earliest',
            consumer_timeout_ms=10000
        )
        
        messages = []
        for message in consumer:
            messages.append(message.value)
            if len(messages) >= 1:
                break
        
        consumer.close()
        
        assert len(messages) == 1
        assert messages[0]['transactionId'] == test_event['transactionId']
        assert messages[0]['amount'] == test_event['amount']
    
    def test_produce_and_consume_iot_sensor_events(self, kafka_producer, kafka_config):
        """Test producing and consuming IoT sensor events"""
        topic = "test-iot-sensor-events"
        test_event = {
            "sensorId": str(uuid.uuid4()),
            "sensorType": "temperature",
            "value": 23.5,
            "unit": "celsius",
            "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
            "location": "Building_A_Floor_1",
            "deviceId": "temp_sensor_001"
        }
        
        # Produce event
        kafka_producer.send(topic, test_event).get(timeout=10)
        
        # Consume event
        consumer = KafkaConsumer(
            topic,
            bootstrap_servers=kafka_config['bootstrap_servers'],
            value_deserializer=kafka_config['value_deserializer'],
            auto_offset_reset='earliest',
            consumer_timeout_ms=10000
        )
        
        messages = []
        for message in consumer:
            messages.append(message.value)
            if len(messages) >= 1:
                break
        
        consumer.close()
        
        assert len(messages) == 1
        assert messages[0]['sensorId'] == test_event['sensorId']
        assert messages[0]['value'] == test_event['value']
    
    def test_batch_message_production(self, kafka_producer):
        """Test producing multiple messages efficiently"""
        topic = "test-user-events"
        batch_size = 100
        
        start_time = time.time()
        
        for i in range(batch_size):
            event = {
                "userId": f"user_{i}",
                "action": "page_view",
                "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
                "page": f"/page_{i % 10}"
            }
            kafka_producer.send(topic, event)
        
        kafka_producer.flush()
        end_time = time.time()
        
        duration = end_time - start_time
        throughput = batch_size / duration
        
        logger.info(f"Produced {batch_size} messages in {duration:.2f}s ({throughput:.2f} msg/s)")
        
        # Assert reasonable throughput (at least 50 messages per second in test environment)
        assert throughput > 50
    
    def test_message_ordering_within_partition(self, kafka_producer, kafka_config):
        """Test that messages with same key maintain order within partition"""
        topic = "test-transaction-events"
        user_id = str(uuid.uuid4())
        
        events = []
        for i in range(10):
            event = {
                "transactionId": str(uuid.uuid4()),
                "userId": user_id,
                "amount": i * 10,
                "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000) + i,
                "sequence": i
            }
            events.append(event)
            kafka_producer.send(topic, key=user_id.encode('utf-8'), value=event)
        
        kafka_producer.flush()
        
        # Consume events
        consumer = KafkaConsumer(
            topic,
            bootstrap_servers=kafka_config['bootstrap_servers'],
            value_deserializer=kafka_config['value_deserializer'],
            auto_offset_reset='earliest',
            consumer_timeout_ms=15000
        )
        
        received_events = []
        for message in consumer:
            if message.value['userId'] == user_id:
                received_events.append(message.value)
                if len(received_events) >= 10:
                    break
        
        consumer.close()
        
        # Verify ordering
        assert len(received_events) == 10
        for i in range(10):
            assert received_events[i]['sequence'] == i
    
    @pytest.mark.timeout(60)
    def test_kafka_cluster_health(self, kafka_config):
        """Test Kafka cluster health and connectivity"""
        from kafka.admin import KafkaAdminClient
        
        admin_client = KafkaAdminClient(
            bootstrap_servers=kafka_config['bootstrap_servers']
        )
        
        # Test cluster metadata
        metadata = admin_client.describe_cluster()
        assert len(metadata.brokers) >= 1
        
        # Test topic listing
        topics = admin_client.list_topics()
        assert len(topics) > 0
        
        admin_client.close()
    
    def test_consumer_group_functionality(self, kafka_producer, kafka_config):
        """Test consumer group functionality and load balancing"""
        topic = "test-user-events"
        group_id = f"test-group-{uuid.uuid4()}"
        
        # Produce test messages
        for i in range(20):
            event = {
                "userId": f"user_{i}",
                "action": "test_action",
                "timestamp": int(datetime.now(timezone.utc).timestamp() * 1000),
                "messageId": i
            }
            kafka_producer.send(topic, event)
        
        kafka_producer.flush()
        
        # Create two consumers in the same group
        consumer1 = KafkaConsumer(
            topic,
            bootstrap_servers=kafka_config['bootstrap_servers'],
            value_deserializer=kafka_config['value_deserializer'],
            group_id=group_id,
            auto_offset_reset='earliest',
            consumer_timeout_ms=10000
        )
        
        consumer2 = KafkaConsumer(
            topic,
            bootstrap_servers=kafka_config['bootstrap_servers'],
            value_deserializer=kafka_config['value_deserializer'],
            group_id=group_id,
            auto_offset_reset='earliest',
            consumer_timeout_ms=10000
        )
        
        messages1 = []
        messages2 = []
        
        # Consume from both consumers
        try:
            for message in consumer1:
                messages1.append(message.value)
                if len(messages1) >= 10:  # Limit to avoid infinite loop
                    break
        except Exception:
            pass
        
        try:
            for message in consumer2:
                messages2.append(message.value)
                if len(messages2) >= 10:  # Limit to avoid infinite loop
                    break
        except Exception:
            pass
        
        consumer1.close()
        consumer2.close()
        
        # Verify that messages are distributed between consumers
        total_messages = len(messages1) + len(messages2)
        assert total_messages > 0
        
        # In a properly functioning consumer group, each message should be consumed by only one consumer
        message_ids1 = {msg['messageId'] for msg in messages1}
        message_ids2 = {msg['messageId'] for msg in messages2}
        assert len(message_ids1.intersection(message_ids2)) == 0  # No duplicate consumption