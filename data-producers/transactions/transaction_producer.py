#!/usr/bin/env python3
"""
Transaction Events Data Producer for Kafka-Flink POC
Generates realistic financial transaction events for fraud detection scenarios
"""

import json
import time
import random
import uuid
from datetime import datetime, timezone, timedelta
from kafka import KafkaProducer
from kafka.errors import KafkaError
import logging
from typing import Dict, List, Optional
import argparse
import math

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class TransactionProducer:
    def __init__(self, bootstrap_servers: str = 'localhost:9092', topic: str = 'transaction-events'):
        """Initialize the transaction event producer"""
        self.bootstrap_servers = bootstrap_servers
        self.topic = topic
        self.producer = None
        self.user_profiles = {}  # Track user spending patterns
        
        # Merchant categories and typical amounts
        self.merchant_categories = {
            'grocery': {'min': 15.0, 'max': 200.0, 'fraud_multiplier': 2.0},
            'gas_station': {'min': 20.0, 'max': 80.0, 'fraud_multiplier': 1.5},
            'restaurant': {'min': 10.0, 'max': 150.0, 'fraud_multiplier': 3.0},
            'retail': {'min': 25.0, 'max': 500.0, 'fraud_multiplier': 4.0},
            'online': {'min': 5.0, 'max': 1000.0, 'fraud_multiplier': 5.0},
            'atm': {'min': 20.0, 'max': 400.0, 'fraud_multiplier': 2.0},
            'hotel': {'min': 80.0, 'max': 400.0, 'fraud_multiplier': 3.0},
            'airline': {'min': 150.0, 'max': 1500.0, 'fraud_multiplier': 2.0},
            'pharmacy': {'min': 5.0, 'max': 100.0, 'fraud_multiplier': 1.5},
            'electronics': {'min': 50.0, 'max': 2000.0, 'fraud_multiplier': 6.0}
        }
        
        self.payment_methods = [
            'credit_card', 'debit_card', 'mobile_payment', 
            'contactless', 'chip_and_pin', 'magnetic_stripe'
        ]
        
        self.currencies = ['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'JPY']
        
        self.countries = ['US', 'UK', 'DE', 'FR', 'CA', 'AU', 'JP', 'IN', 'BR', 'MX']
        
        # Risk factors for fraud detection
        self.high_risk_countries = ['RU', 'CN', 'NG', 'PK']
        self.high_risk_hours = list(range(0, 6))  # 12 AM - 6 AM
        
    def connect(self):
        """Establish connection to Kafka"""
        try:
            self.producer = KafkaProducer(
                bootstrap_servers=self.bootstrap_servers,
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',
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
        """Generate a user ID (reuse some for pattern building)"""
        # 80% chance of reusing existing user, 20% new user
        if self.user_profiles and random.random() < 0.8:
            return random.choice(list(self.user_profiles.keys()))
        else:
            return f"user_{random.randint(10000, 99999)}"
    
    def get_or_create_user_profile(self, user_id: str) -> Dict:
        """Get or create user spending profile"""
        if user_id not in self.user_profiles:
            self.user_profiles[user_id] = {
                'average_amount': random.uniform(50, 300),
                'preferred_categories': random.sample(list(self.merchant_categories.keys()), 3),
                'home_country': random.choice(self.countries),
                'typical_hours': random.sample(range(6, 23), 8),  # Normal business hours
                'card_numbers': [f"****-****-****-{random.randint(1000, 9999)}" for _ in range(random.randint(1, 3))],
                'fraud_incidents': 0,
                'last_transaction_time': None,
                'last_location': None
            }
        return self.user_profiles[user_id]
    
    def calculate_risk_score(self, transaction: Dict, user_profile: Dict) -> float:
        """Calculate fraud risk score (0.0 - 1.0)"""
        risk_score = 0.0
        
        # Amount-based risk
        amount = transaction['amount']
        avg_amount = user_profile['average_amount']
        if amount > avg_amount * 3:
            risk_score += 0.3
        elif amount > avg_amount * 5:
            risk_score += 0.5
        elif amount > avg_amount * 10:
            risk_score += 0.7
        
        # Time-based risk
        hour = datetime.fromisoformat(transaction['timestamp'].replace('Z', '+00:00')).hour
        if hour in self.high_risk_hours:
            risk_score += 0.2
        
        # Location-based risk
        if transaction['country'] in self.high_risk_countries:
            risk_score += 0.4
        elif transaction['country'] != user_profile['home_country']:
            risk_score += 0.1
        
        # Category-based risk
        if transaction['merchantCategory'] not in user_profile['preferred_categories']:
            risk_score += 0.1
        
        # Velocity-based risk (rapid transactions)
        if user_profile['last_transaction_time']:
            time_diff = (datetime.fromisoformat(transaction['timestamp'].replace('Z', '+00:00')) - 
                        user_profile['last_transaction_time']).total_seconds()
            if time_diff < 60:  # Less than 1 minute
                risk_score += 0.3
            elif time_diff < 300:  # Less than 5 minutes
                risk_score += 0.1
        
        # Payment method risk
        if transaction['paymentMethod'] == 'magnetic_stripe':
            risk_score += 0.1
        
        # Historical fraud incidents
        risk_score += min(user_profile['fraud_incidents'] * 0.1, 0.3)
        
        return min(risk_score, 1.0)
    
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
            'MX': {'lat': (14.0, 33.0), 'lon': (-117.0, -86.0)},
            'RU': {'lat': (41.0, 82.0), 'lon': (19.0, 169.0)},
            'CN': {'lat': (18.0, 54.0), 'lon': (73.0, 135.0)},
            'NG': {'lat': (4.0, 14.0), 'lon': (3.0, 15.0)},
            'PK': {'lat': (24.0, 37.0), 'lon': (61.0, 77.0)}
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
    
    def generate_transaction(self, force_fraud: bool = False) -> Dict:
        """Generate a realistic transaction event"""
        user_id = self.generate_user_id()
        user_profile = self.get_or_create_user_profile(user_id)
        
        # Determine if this should be a fraudulent transaction
        is_fraud = force_fraud or random.random() < 0.05  # 5% fraud rate
        
        # Select merchant category
        if is_fraud:
            # Fraudulent transactions often in high-value categories
            category = random.choice(['electronics', 'online', 'retail', 'airline'])
        else:
            # Normal transactions follow user preferences
            if random.random() < 0.7:
                category = random.choice(user_profile['preferred_categories'])
            else:
                category = random.choice(list(self.merchant_categories.keys()))
        
        category_info = self.merchant_categories[category]
        
        # Generate amount
        if is_fraud:
            # Fraudulent amounts are often higher or unusual
            base_amount = random.uniform(category_info['min'], category_info['max'])
            amount = base_amount * category_info['fraud_multiplier'] * random.uniform(1.5, 4.0)
        else:
            # Normal amounts around user's average
            avg = user_profile['average_amount']
            amount = random.uniform(
                max(category_info['min'], avg * 0.3),
                min(category_info['max'], avg * 2.0)
            )
        
        amount = round(amount, 2)
        
        # Generate location
        if is_fraud and random.random() < 0.3:
            # Some fraud from high-risk countries
            country = random.choice(self.high_risk_countries)
        elif is_fraud and random.random() < 0.5:
            # Some fraud from different country than user's home
            country = random.choice([c for c in self.countries if c != user_profile['home_country']])
        else:
            # Normal transactions mostly from home country
            if random.random() < 0.8:
                country = user_profile['home_country']
            else:
                country = random.choice(self.countries)
        
        # Generate transaction
        transaction = {
            'timestamp': datetime.now(timezone.utc).isoformat(),
            'transactionId': str(uuid.uuid4()),
            'userId': user_id,
            'amount': amount,
            'currency': random.choice(self.currencies),
            'merchantId': f"merchant_{random.randint(1000, 9999)}",
            'merchantName': f"Merchant {random.randint(1, 1000)}",
            'merchantCategory': category,
            'paymentMethod': random.choice(self.payment_methods),
            'cardNumber': random.choice(user_profile['card_numbers']),
            'country': country,
            'location': self.generate_coordinates(country),
            'ipAddress': f"{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}.{random.randint(1,255)}",
            'userAgent': 'transaction-app/1.0',
            'deviceId': f"device_{random.randint(10000, 99999)}",
            'isOnline': category == 'online' or random.random() < 0.3,
            'authorizationCode': f"AUTH{random.randint(100000, 999999)}",
            'processingTime': random.randint(50, 2000),  # ms
            'isFraud': is_fraud
        }
        
        # Calculate risk score
        risk_score = self.calculate_risk_score(transaction, user_profile)
        transaction['riskScore'] = round(risk_score, 3)
        
        # Set transaction status based on risk and fraud
        if is_fraud and risk_score > 0.7:
            transaction['status'] = random.choice(['declined', 'pending_review'])
        elif risk_score > 0.8:
            transaction['status'] = 'pending_review'
        else:
            transaction['status'] = 'approved'
        
        # Update user profile
        user_profile['last_transaction_time'] = datetime.fromisoformat(transaction['timestamp'].replace('Z', '+00:00'))
        user_profile['last_location'] = country
        if is_fraud:
            user_profile['fraud_incidents'] += 1
        
        # Add additional metadata
        transaction['metadata'] = {
            'processor': random.choice(['visa', 'mastercard', 'amex', 'discover']),
            'terminal_id': f"terminal_{random.randint(1000, 9999)}",
            'batch_number': f"batch_{random.randint(100, 999)}",
            'response_code': '00' if transaction['status'] == 'approved' else random.choice(['51', '05', '14'])
        }
        
        return transaction
    
    def send_event(self, transaction: Dict) -> bool:
        """Send transaction to Kafka topic"""
        try:
            # Use userId as partition key for even distribution
            future = self.producer.send(
                self.topic,
                key=transaction['userId'],
                value=transaction
            )
            
            # Non-blocking send with callback
            future.add_callback(self._on_send_success)
            future.add_errback(self._on_send_error)
            
            return True
        except Exception as e:
            logger.error(f"Failed to send transaction: {e}")
            return False
    
    def _on_send_success(self, record_metadata):
        """Callback for successful sends"""
        logger.debug(f"Transaction sent to topic {record_metadata.topic} "
                    f"partition {record_metadata.partition} "
                    f"offset {record_metadata.offset}")
    
    def _on_send_error(self, exception):
        """Callback for send errors"""
        logger.error(f"Failed to send transaction: {exception}")
    
    def produce_transactions(self, events_per_second: float = 5, duration_seconds: int = 60):
        """Produce transactions at specified rate for given duration"""
        if not self.connect():
            return
        
        interval = 1.0 / events_per_second if events_per_second > 0 else 1.0
        end_time = time.time() + duration_seconds
        transaction_count = 0
        fraud_count = 0
        
        logger.info(f"Starting to produce transactions at {events_per_second} transactions/second for {duration_seconds} seconds")
        
        try:
            while time.time() < end_time:
                start_time = time.time()
                
                # Generate and send transaction
                transaction = self.generate_transaction()
                if self.send_event(transaction):
                    transaction_count += 1
                    if transaction['isFraud']:
                        fraud_count += 1
                    
                    if transaction_count % 50 == 0:
                        fraud_rate = (fraud_count / transaction_count) * 100
                        logger.info(f"Produced {transaction_count} transactions ({fraud_count} fraud, {fraud_rate:.1f}%)")
                
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
            
            fraud_rate = (fraud_count / transaction_count) * 100 if transaction_count > 0 else 0
            logger.info(f"Total transactions produced: {transaction_count}")
            logger.info(f"Fraudulent transactions: {fraud_count} ({fraud_rate:.1f}%)")
    
    def produce_continuous(self, events_per_second: float = 5):
        """Produce transactions continuously until interrupted"""
        if not self.connect():
            return
        
        interval = 1.0 / events_per_second if events_per_second > 0 else 1.0
        transaction_count = 0
        fraud_count = 0
        
        logger.info(f"Starting continuous transaction production at {events_per_second} transactions/second")
        logger.info("Press Ctrl+C to stop")
        
        try:
            while True:
                start_time = time.time()
                
                # Generate and send transaction
                transaction = self.generate_transaction()
                if self.send_event(transaction):
                    transaction_count += 1
                    if transaction['isFraud']:
                        fraud_count += 1
                    
                    if transaction_count % 50 == 0:
                        fraud_rate = (fraud_count / transaction_count) * 100
                        logger.info(f"Produced {transaction_count} transactions ({fraud_count} fraud, {fraud_rate:.1f}%)")
                
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
            
            fraud_rate = (fraud_count / transaction_count) * 100 if transaction_count > 0 else 0
            logger.info(f"Total transactions produced: {transaction_count}")
            logger.info(f"Fraudulent transactions: {fraud_count} ({fraud_rate:.1f}%)")

def main():
    parser = argparse.ArgumentParser(description='Transaction Events Data Producer')
    parser.add_argument('--bootstrap-servers', default='localhost:9092',
                       help='Kafka bootstrap servers')
    parser.add_argument('--topic', default='transaction-events',
                       help='Kafka topic name')
    parser.add_argument('--rate', type=float, default=5,
                       help='Transactions per second')
    parser.add_argument('--duration', type=int, default=0,
                       help='Duration in seconds (0 for continuous)')
    
    args = parser.parse_args()
    
    producer = TransactionProducer(
        bootstrap_servers=args.bootstrap_servers,
        topic=args.topic
    )
    
    if args.duration > 0:
        producer.produce_transactions(
            events_per_second=args.rate,
            duration_seconds=args.duration
        )
    else:
        producer.produce_continuous(events_per_second=args.rate)

if __name__ == '__main__':
    main()