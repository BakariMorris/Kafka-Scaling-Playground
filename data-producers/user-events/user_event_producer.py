#!/usr/bin/env python3
"""
User Events Data Producer for Kafka-Flink POC
Generates realistic user behavior events for e-commerce scenarios
"""

import json
import time
import random
import uuid
from datetime import datetime, timezone
from kafka import KafkaProducer
from kafka.errors import KafkaError
import logging
from typing import Dict, List
import argparse

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class UserEventProducer:
    def __init__(self, bootstrap_servers: str = 'localhost:9092', topic: str = 'user-events'):
        """Initialize the user event producer"""
        self.bootstrap_servers = bootstrap_servers
        self.topic = topic
        self.producer = None
        self.session_store = {}  # Track active user sessions
        
        # Sample data for realistic events
        self.user_agents = [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
            'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36',
            'Mozilla/5.0 (iPhone; CPU iPhone OS 14_7_1 like Mac OS X)',
            'Mozilla/5.0 (Android 11; Mobile; rv:68.0) Gecko/68.0'
        ]
        
        self.pages = [
            '/home', '/products', '/category/electronics', '/category/clothing',
            '/product/laptop-pro', '/product/smartphone-x', '/cart', '/checkout',
            '/account', '/wishlist', '/search', '/contact', '/about'
        ]
        
        self.event_types = [
            'page_view', 'click', 'scroll', 'search', 'add_to_cart',
            'remove_from_cart', 'purchase', 'login', 'logout', 'signup'
        ]
        
        self.referrers = [
            'https://google.com', 'https://facebook.com', 'https://twitter.com',
            'direct', 'https://bing.com', 'https://youtube.com'
        ]
        
        self.countries = ['US', 'UK', 'DE', 'FR', 'CA', 'AU', 'JP', 'IN', 'BR', 'MX']
        
    def connect(self):
        """Establish connection to Kafka"""
        try:
            self.producer = KafkaProducer(
                bootstrap_servers=self.bootstrap_servers,
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',  # Wait for all replicas to acknowledge
                retries=3,
                max_in_flight_requests_per_connection=1,
                batch_size=16384,
                linger_ms=10,
                compression_type='snappy'
            )
            logger.info(f"Connected to Kafka brokers: {self.bootstrap_servers}")
            return True
        except Exception as e:
            logger.error(f"Failed to connect to Kafka: {e}")
            return False
    
    def generate_user_id(self) -> str:
        """Generate a realistic user ID"""
        return f"user_{random.randint(1000, 9999)}"
    
    def generate_session_id(self) -> str:
        """Generate a unique session ID"""
        return str(uuid.uuid4())
    
    def get_or_create_session(self, user_id: str) -> str:
        """Get existing session or create new one for user"""
        current_time = time.time()
        
        # Clean up old sessions (older than 30 minutes)
        expired_sessions = []
        for uid, (session_id, last_activity) in self.session_store.items():
            if current_time - last_activity > 1800:  # 30 minutes
                expired_sessions.append(uid)
        
        for uid in expired_sessions:
            del self.session_store[uid]
        
        # Get or create session for user
        if user_id in self.session_store:
            session_id, _ = self.session_store[user_id]
        else:
            session_id = self.generate_session_id()
        
        # Update last activity
        self.session_store[user_id] = (session_id, current_time)
        return session_id
    
    def generate_coordinates(self, country: str) -> Dict[str, float]:
        """Generate realistic coordinates based on country"""
        coord_ranges = {
            'US': {'lat': (25.0, 49.0), 'lon': (-125.0, -66.0)},
            'UK': {'lat': (50.0, 60.0), 'lon': (-8.0, 2.0)},
            'DE': {'lat': (47.0, 55.0), 'lon': (6.0, 15.0)},
            'FR': {'lat': (42.0, 51.0), 'lon': (-5.0, 8.0)},
            'CA': {'lat': (42.0, 70.0), 'lon': (-140.0, -52.0)},
            'AU': {'lat': (-44.0, -10.0), 'lon': (113.0, 154.0)},
            'JP': {'lat': (31.0, 46.0), 'lon': (130.0, 146.0)},
            'IN': {'lat': (8.0, 37.0), 'lon': (68.0, 97.0)},
            'BR': {'lat': (-34.0, 5.0), 'lon': (-74.0, -35.0)},
            'MX': {'lat': (14.0, 33.0), 'lon': (-117.0, -86.0)}
        }
        
        if country in coord_ranges:
            lat_range = coord_ranges[country]['lat']
            lon_range = coord_ranges[country]['lon']
            return {
                'lat': round(random.uniform(lat_range[0], lat_range[1]), 4),
                'lon': round(random.uniform(lon_range[0], lon_range[1]), 4)
            }
        else:
            return {'lat': 0.0, 'lon': 0.0}
    
    def generate_event(self) -> Dict:
        """Generate a realistic user event"""
        user_id = self.generate_user_id()
        session_id = self.get_or_create_session(user_id)
        event_type = random.choice(self.event_types)
        country = random.choice(self.countries)
        
        # Base event structure
        event = {
            'timestamp': datetime.now(timezone.utc).isoformat(),
            'userId': user_id,
            'sessionId': session_id,
            'eventType': event_type,
            'userAgent': random.choice(self.user_agents),
            'ipAddress': f"{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}",
            'country': country,
            'location': self.generate_coordinates(country),
            'referrer': random.choice(self.referrers)
        }
        
        # Add event-specific data
        if event_type == 'page_view':
            event.update({
                'page': random.choice(self.pages),
                'loadTime': random.randint(200, 3000),
                'duration': random.randint(1000, 300000)  # Time on page in ms
            })
        
        elif event_type == 'click':
            event.update({
                'page': random.choice(self.pages),
                'element': random.choice(['button', 'link', 'image', 'menu']),
                'elementId': f"element_{random.randint(1, 100)}",
                'coordinates': {'x': random.randint(0, 1920), 'y': random.randint(0, 1080)}
            })
        
        elif event_type == 'search':
            search_terms = ['laptop', 'phone', 'shoes', 'shirt', 'headphones', 'book', 'camera']
            event.update({
                'query': random.choice(search_terms),
                'resultsCount': random.randint(0, 1000),
                'page': '/search'
            })
        
        elif event_type in ['add_to_cart', 'remove_from_cart']:
            event.update({
                'productId': f"prod_{random.randint(1000, 9999)}",
                'productName': f"Product {random.randint(1, 100)}",
                'price': round(random.uniform(10.0, 500.0), 2),
                'quantity': random.randint(1, 5),
                'category': random.choice(['electronics', 'clothing', 'books', 'home'])
            })
        
        elif event_type == 'purchase':
            num_items = random.randint(1, 5)
            total_amount = round(random.uniform(20.0, 1000.0), 2)
            event.update({
                'orderId': f"order_{random.randint(10000, 99999)}",
                'totalAmount': total_amount,
                'currency': 'USD',
                'itemCount': num_items,
                'paymentMethod': random.choice(['credit_card', 'paypal', 'apple_pay', 'debit_card'])
            })
        
        # Add some random properties for enrichment
        event['deviceType'] = random.choice(['desktop', 'mobile', 'tablet'])
        event['browser'] = random.choice(['chrome', 'firefox', 'safari', 'edge'])
        event['isNewUser'] = random.choice([True, False])
        
        return event
    
    def send_event(self, event: Dict) -> bool:
        """Send event to Kafka topic"""
        try:
            # Use userId as partition key for even distribution
            future = self.producer.send(
                self.topic,
                key=event['userId'],
                value=event
            )
            
            # Non-blocking send with callback
            future.add_callback(self._on_send_success)
            future.add_errback(self._on_send_error)
            
            return True
        except Exception as e:
            logger.error(f"Failed to send event: {e}")
            return False
    
    def _on_send_success(self, record_metadata):
        """Callback for successful sends"""
        logger.debug(f"Event sent to topic {record_metadata.topic} "
                    f"partition {record_metadata.partition} "
                    f"offset {record_metadata.offset}")
    
    def _on_send_error(self, exception):
        """Callback for send errors"""
        logger.error(f"Failed to send event: {exception}")
    
    def produce_events(self, events_per_second: float = 10, duration_seconds: int = 60):
        """Produce events at specified rate for given duration"""
        if not self.connect():
            return
        
        interval = 1.0 / events_per_second if events_per_second > 0 else 1.0
        end_time = time.time() + duration_seconds
        event_count = 0
        
        logger.info(f"Starting to produce events at {events_per_second} events/second for {duration_seconds} seconds")
        
        try:
            while time.time() < end_time:
                start_time = time.time()
                
                # Generate and send event
                event = self.generate_event()
                if self.send_event(event):
                    event_count += 1
                    
                    if event_count % 100 == 0:
                        logger.info(f"Produced {event_count} events")
                
                # Sleep to maintain rate
                elapsed = time.time() - start_time
                sleep_time = max(0, interval - elapsed)
                if sleep_time > 0:
                    time.sleep(sleep_time)
                    
        except KeyboardInterrupt:
            logger.info("Interrupted by user")
        finally:
            if self.producer:
                self.producer.flush()
                self.producer.close()
            
            logger.info(f"Total events produced: {event_count}")
    
    def produce_continuous(self, events_per_second: float = 10):
        """Produce events continuously until interrupted"""
        if not self.connect():
            return
        
        interval = 1.0 / events_per_second if events_per_second > 0 else 1.0
        event_count = 0
        
        logger.info(f"Starting continuous event production at {events_per_second} events/second")
        logger.info("Press Ctrl+C to stop")
        
        try:
            while True:
                start_time = time.time()
                
                # Generate and send event
                event = self.generate_event()
                if self.send_event(event):
                    event_count += 1
                    
                    if event_count % 100 == 0:
                        logger.info(f"Produced {event_count} events")
                
                # Sleep to maintain rate
                elapsed = time.time() - start_time
                sleep_time = max(0, interval - elapsed)
                if sleep_time > 0:
                    time.sleep(sleep_time)
                    
        except KeyboardInterrupt:
            logger.info("Interrupted by user")
        finally:
            if self.producer:
                self.producer.flush()
                self.producer.close()
            
            logger.info(f"Total events produced: {event_count}")

def main():
    parser = argparse.ArgumentParser(description='User Events Data Producer')
    parser.add_argument('--bootstrap-servers', default='localhost:9092',
                       help='Kafka bootstrap servers')
    parser.add_argument('--topic', default='user-events',
                       help='Kafka topic name')
    parser.add_argument('--rate', type=float, default=10,
                       help='Events per second')
    parser.add_argument('--duration', type=int, default=0,
                       help='Duration in seconds (0 for continuous)')
    
    args = parser.parse_args()
    
    producer = UserEventProducer(
        bootstrap_servers=args.bootstrap_servers,
        topic=args.topic
    )
    
    if args.duration > 0:
        producer.produce_events(
            events_per_second=args.rate,
            duration_seconds=args.duration
        )
    else:
        producer.produce_continuous(events_per_second=args.rate)

if __name__ == '__main__':
    main()