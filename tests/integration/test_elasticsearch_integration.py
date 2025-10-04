import pytest
import json
import time
import uuid
from datetime import datetime, timezone, timedelta
from elasticsearch import Elasticsearch, NotFoundError
from kafka import KafkaProducer
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class TestElasticsearchIntegration:
    """Integration tests for Elasticsearch data storage and retrieval"""
    
    @pytest.fixture(scope="class")
    def elasticsearch_client(self):
        es = Elasticsearch(['http://elasticsearch-test:9200'])
        
        # Wait for Elasticsearch to be ready
        for _ in range(30):
            try:
                es.cluster.health(wait_for_status='yellow', timeout='1s')
                break
            except Exception:
                time.sleep(1)
        else:
            pytest.fail("Elasticsearch not ready after 30 seconds")
        
        return es
    
    @pytest.fixture(scope="class")
    def kafka_producer(self):
        return KafkaProducer(
            bootstrap_servers=['kafka-test:29092'],
            value_serializer=lambda x: json.dumps(x).encode('utf-8')
        )
    
    @pytest.fixture(autouse=True)
    def cleanup_indices(self, elasticsearch_client):
        """Clean up test indices before and after each test"""
        test_indices = [
            'test-user-events*',
            'test-transaction-events*',
            'test-iot-sensor-events*',
            'test-fraud-alerts*',
            'test-anomaly-alerts*'
        ]
        
        for index_pattern in test_indices:
            try:
                elasticsearch_client.indices.delete(index=index_pattern)
            except NotFoundError:
                pass
        
        yield
        
        # Cleanup after test
        for index_pattern in test_indices:
            try:
                elasticsearch_client.indices.delete(index=index_pattern)
            except NotFoundError:
                pass
    
    def test_elasticsearch_cluster_health(self, elasticsearch_client):
        """Test Elasticsearch cluster health and connectivity"""
        health = elasticsearch_client.cluster.health()
        
        assert health['status'] in ['green', 'yellow']
        assert health['number_of_nodes'] >= 1
        assert health['number_of_data_nodes'] >= 1
    
    def test_create_and_query_user_events_index(self, elasticsearch_client):
        """Test creating and querying user events index"""
        index_name = "test-user-events"
        
        # Create index with mapping
        mapping = {
            "mappings": {
                "properties": {
                    "userId": {"type": "keyword"},
                    "action": {"type": "keyword"},
                    "timestamp": {"type": "date"},
                    "sessionId": {"type": "keyword"},
                    "userAgent": {"type": "text"},
                    "ipAddress": {"type": "ip"}
                }
            }
        }
        
        elasticsearch_client.indices.create(index=index_name, body=mapping)
        
        # Insert test documents
        test_docs = []
        for i in range(10):
            doc = {
                "userId": f"user_{i}",
                "action": "login" if i % 2 == 0 else "logout",
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "sessionId": f"session_{i // 2}",
                "userAgent": "test-agent",
                "ipAddress": f"192.168.1.{100 + i}"
            }
            test_docs.append(doc)
            
            elasticsearch_client.index(
                index=index_name,
                id=str(uuid.uuid4()),
                body=doc
            )
        
        # Refresh index to make documents searchable
        elasticsearch_client.indices.refresh(index=index_name)
        
        # Test basic search
        search_result = elasticsearch_client.search(
            index=index_name,
            body={"query": {"match_all": {}}}
        )
        
        assert search_result['hits']['total']['value'] == 10
        
        # Test filtered search
        login_search = elasticsearch_client.search(
            index=index_name,
            body={"query": {"term": {"action": "login"}}}
        )
        
        assert login_search['hits']['total']['value'] == 5
    
    def test_create_and_query_transaction_events_index(self, elasticsearch_client):
        """Test creating and querying transaction events index"""
        index_name = "test-transaction-events"
        
        # Create index with mapping optimized for transaction data
        mapping = {
            "mappings": {
                "properties": {
                    "transactionId": {"type": "keyword"},
                    "userId": {"type": "keyword"},
                    "amount": {"type": "double"},
                    "currency": {"type": "keyword"},
                    "timestamp": {"type": "date"},
                    "location": {"type": "keyword"},
                    "merchantId": {"type": "keyword"},
                    "category": {"type": "keyword"},
                    "isFraud": {"type": "boolean"}
                }
            }
        }
        
        elasticsearch_client.indices.create(index=index_name, body=mapping)
        
        # Insert transaction data with varying amounts
        amounts = [10.50, 25.75, 100.00, 250.00, 500.00, 1000.00, 2500.00, 5000.00, 7500.00, 10000.00]
        
        for i, amount in enumerate(amounts):
            doc = {
                "transactionId": str(uuid.uuid4()),
                "userId": f"user_{i % 3}",  # 3 different users
                "amount": amount,
                "currency": "USD",
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "location": ["New York", "San Francisco", "Chicago"][i % 3],
                "merchantId": f"merchant_{i % 5}",
                "category": ["groceries", "electronics", "gas", "dining", "shopping"][i % 5],
                "isFraud": amount > 5000  # Mark high amounts as fraud for testing
            }
            
            elasticsearch_client.index(
                index=index_name,
                id=str(uuid.uuid4()),
                body=doc
            )
        
        elasticsearch_client.indices.refresh(index=index_name)
        
        # Test range queries
        high_value_transactions = elasticsearch_client.search(
            index=index_name,
            body={
                "query": {
                    "range": {
                        "amount": {
                            "gte": 1000
                        }
                    }
                }
            }
        )
        
        assert high_value_transactions['hits']['total']['value'] == 4
        
        # Test fraud detection query
        fraud_transactions = elasticsearch_client.search(
            index=index_name,
            body={"query": {"term": {"isFraud": True}}}
        )
        
        assert fraud_transactions['hits']['total']['value'] == 3
    
    def test_create_and_query_iot_sensor_events_index(self, elasticsearch_client):
        """Test creating and querying IoT sensor events index"""
        index_name = "test-iot-sensor-events"
        
        # Create index with mapping for IoT sensor data
        mapping = {
            "mappings": {
                "properties": {
                    "sensorId": {"type": "keyword"},
                    "sensorType": {"type": "keyword"},
                    "value": {"type": "double"},
                    "unit": {"type": "keyword"},
                    "timestamp": {"type": "date"},
                    "location": {"type": "keyword"},
                    "deviceId": {"type": "keyword"},
                    "isAnomaly": {"type": "boolean"}
                }
            }
        }
        
        elasticsearch_client.indices.create(index=index_name, body=mapping)
        
        # Insert sensor data for different sensor types
        sensor_types = ["temperature", "humidity", "pressure", "light", "motion"]
        locations = ["Building_A_Floor_1", "Building_A_Floor_2", "Building_B_Floor_1"]
        
        for i in range(50):
            sensor_type = sensor_types[i % len(sensor_types)]
            location = locations[i % len(locations)]
            
            # Generate realistic sensor values
            if sensor_type == "temperature":
                value = 20 + (i % 20)  # 20-40°C
                is_anomaly = value > 35
            elif sensor_type == "humidity":
                value = 30 + (i % 40)  # 30-70%
                is_anomaly = value > 65
            elif sensor_type == "pressure":
                value = 1000 + (i % 100)  # 1000-1100 hPa
                is_anomaly = value > 1080
            elif sensor_type == "light":
                value = i % 1000  # 0-1000 lux
                is_anomaly = value > 900
            else:  # motion
                value = i % 2  # 0 or 1
                is_anomaly = False
            
            doc = {
                "sensorId": f"sensor_{sensor_type}_{i % 10}",
                "sensorType": sensor_type,
                "value": value,
                "unit": {
                    "temperature": "celsius",
                    "humidity": "percent",
                    "pressure": "hPa",
                    "light": "lux",
                    "motion": "boolean"
                }[sensor_type],
                "timestamp": (datetime.now(timezone.utc) - timedelta(minutes=i)).isoformat(),
                "location": location,
                "deviceId": f"device_{location.lower()}_{sensor_type}",
                "isAnomaly": is_anomaly
            }
            
            elasticsearch_client.index(
                index=index_name,
                id=str(uuid.uuid4()),
                body=doc
            )
        
        elasticsearch_client.indices.refresh(index=index_name)
        
        # Test sensor type aggregation
        sensor_agg_result = elasticsearch_client.search(
            index=index_name,
            body={
                "size": 0,
                "aggs": {
                    "sensor_types": {
                        "terms": {
                            "field": "sensorType",
                            "size": 10
                        }
                    }
                }
            }
        )
        
        sensor_buckets = sensor_agg_result['aggregations']['sensor_types']['buckets']
        assert len(sensor_buckets) == 5
        
        # Test anomaly detection query
        anomaly_query = elasticsearch_client.search(
            index=index_name,
            body={"query": {"term": {"isAnomaly": True}}}
        )
        
        assert anomaly_query['hits']['total']['value'] > 0
    
    def test_time_based_queries_and_aggregations(self, elasticsearch_client):
        """Test time-based queries and aggregations"""
        index_name = "test-transaction-events"
        
        # Create index
        elasticsearch_client.indices.create(
            index=index_name,
            body={
                "mappings": {
                    "properties": {
                        "timestamp": {"type": "date"},
                        "amount": {"type": "double"},
                        "userId": {"type": "keyword"}
                    }
                }
            }
        )
        
        # Insert transactions over different time periods
        base_time = datetime.now(timezone.utc)
        
        for i in range(24):  # 24 hours of data
            for j in range(5):  # 5 transactions per hour
                doc = {
                    "transactionId": str(uuid.uuid4()),
                    "userId": f"user_{j}",
                    "amount": 50 + (i * j),
                    "timestamp": (base_time - timedelta(hours=i, minutes=j*10)).isoformat()
                }
                
                elasticsearch_client.index(
                    index=index_name,
                    id=str(uuid.uuid4()),
                    body=doc
                )
        
        elasticsearch_client.indices.refresh(index=index_name)
        
        # Test time range query (last 12 hours)
        recent_transactions = elasticsearch_client.search(
            index=index_name,
            body={
                "query": {
                    "range": {
                        "timestamp": {
                            "gte": (base_time - timedelta(hours=12)).isoformat()
                        }
                    }
                }
            }
        )
        
        assert recent_transactions['hits']['total']['value'] == 60  # 12 hours * 5 transactions
        
        # Test date histogram aggregation
        hourly_agg = elasticsearch_client.search(
            index=index_name,
            body={
                "size": 0,
                "aggs": {
                    "transactions_over_time": {
                        "date_histogram": {
                            "field": "timestamp",
                            "calendar_interval": "1h"
                        },
                        "aggs": {
                            "total_amount": {
                                "sum": {
                                    "field": "amount"
                                }
                            }
                        }
                    }
                }
            }
        )
        
        hourly_buckets = hourly_agg['aggregations']['transactions_over_time']['buckets']
        assert len(hourly_buckets) >= 24
    
    def test_bulk_indexing_performance(self, elasticsearch_client):
        """Test bulk indexing performance"""
        index_name = "test-bulk-performance"
        
        # Create index
        elasticsearch_client.indices.create(
            index=index_name,
            body={
                "mappings": {
                    "properties": {
                        "id": {"type": "keyword"},
                        "value": {"type": "double"},
                        "timestamp": {"type": "date"}
                    }
                }
            }
        )
        
        # Prepare bulk data
        bulk_size = 1000
        bulk_body = []
        
        for i in range(bulk_size):
            action = {"index": {"_index": index_name, "_id": str(uuid.uuid4())}}
            doc = {
                "id": f"doc_{i}",
                "value": i * 1.5,
                "timestamp": datetime.now(timezone.utc).isoformat()
            }
            
            bulk_body.extend([action, doc])
        
        # Perform bulk indexing
        start_time = time.time()
        result = elasticsearch_client.bulk(body=bulk_body)
        end_time = time.time()
        
        duration = end_time - start_time
        throughput = bulk_size / duration
        
        logger.info(f"Bulk indexed {bulk_size} documents in {duration:.2f}s ({throughput:.2f} docs/s)")
        
        # Verify no errors in bulk operation
        assert not result['errors']
        
        # Verify documents were indexed
        elasticsearch_client.indices.refresh(index=index_name)
        count_result = elasticsearch_client.count(index=index_name)
        assert count_result['count'] == bulk_size
        
        # Assert reasonable performance (at least 100 docs/sec in test environment)
        assert throughput > 100
    
    def test_search_performance_and_pagination(self, elasticsearch_client):
        """Test search performance and pagination"""
        index_name = "test-search-performance"
        
        # Create and populate index
        elasticsearch_client.indices.create(
            index=index_name,
            body={
                "mappings": {
                    "properties": {
                        "category": {"type": "keyword"},
                        "value": {"type": "double"},
                        "tags": {"type": "keyword"}
                    }
                }
            }
        )
        
        # Index documents in bulk
        docs_count = 5000
        bulk_body = []
        
        for i in range(docs_count):
            action = {"index": {"_index": index_name, "_id": str(i)}}
            doc = {
                "category": f"category_{i % 10}",
                "value": i * 0.1,
                "tags": [f"tag_{i % 5}", f"tag_{(i + 1) % 5}"]
            }
            bulk_body.extend([action, doc])
        
        elasticsearch_client.bulk(body=bulk_body)
        elasticsearch_client.indices.refresh(index=index_name)
        
        # Test search performance
        start_time = time.time()
        search_result = elasticsearch_client.search(
            index=index_name,
            body={
                "query": {
                    "bool": {
                        "must": [
                            {"range": {"value": {"gte": 100, "lte": 400}}},
                            {"term": {"category": "category_5"}}
                        ]
                    }
                }
            },
            size=100
        )
        end_time = time.time()
        
        search_duration = end_time - start_time
        logger.info(f"Search completed in {search_duration:.3f}s")
        
        # Verify search results
        assert search_result['hits']['total']['value'] > 0
        assert search_duration < 1.0  # Search should complete within 1 second
        
        # Test pagination with scroll
        scroll_size = 100
        scroll_result = elasticsearch_client.search(
            index=index_name,
            body={"query": {"match_all": {}}},
            size=scroll_size,
            scroll='2m'
        )
        
        total_docs_scrolled = len(scroll_result['hits']['hits'])
        scroll_id = scroll_result['_scroll_id']
        
        # Scroll through more results
        while len(scroll_result['hits']['hits']) > 0:
            scroll_result = elasticsearch_client.scroll(
                scroll_id=scroll_id,
                scroll='2m'
            )
            total_docs_scrolled += len(scroll_result['hits']['hits'])
            
            if total_docs_scrolled >= 1000:  # Limit for test
                break
        
        # Clean up scroll
        elasticsearch_client.clear_scroll(scroll_id=scroll_id)
        
        assert total_docs_scrolled >= 1000
    
    def test_index_templates_and_lifecycle_management(self, elasticsearch_client):
        """Test index templates and basic lifecycle management"""
        template_name = "test-template"
        
        # Create index template
        template_body = {
            "index_patterns": ["test-events-*"],
            "template": {
                "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0
                },
                "mappings": {
                    "properties": {
                        "timestamp": {"type": "date"},
                        "event_type": {"type": "keyword"},
                        "data": {"type": "object"}
                    }
                }
            }
        }
        
        elasticsearch_client.indices.put_index_template(
            name=template_name,
            body=template_body
        )
        
        # Create index that matches template pattern
        test_index = "test-events-2023-12"
        elasticsearch_client.indices.create(index=test_index)
        
        # Verify template was applied
        index_mapping = elasticsearch_client.indices.get_mapping(index=test_index)
        properties = index_mapping[test_index]['mappings']['properties']
        
        assert 'timestamp' in properties
        assert properties['timestamp']['type'] == 'date'
        assert properties['event_type']['type'] == 'keyword'
        
        # Clean up
        elasticsearch_client.indices.delete(index=test_index)
        elasticsearch_client.indices.delete_index_template(name=template_name)