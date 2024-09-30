#!/usr/bin/env python3
"""
IoT Sensor Data Producer for Kafka-Flink POC
Generates realistic IoT sensor events for anomaly detection scenarios
"""

import json
import time
import random
import uuid
import math
from datetime import datetime, timezone, timedelta
from kafka import KafkaProducer
from kafka.errors import KafkaError
import logging
from typing import Dict, List, Optional
import argparse

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class IoTSensorProducer:
    def __init__(self, bootstrap_servers: str = 'localhost:9092', topic: str = 'iot-sensor-events'):
        """Initialize the IoT sensor event producer"""
        self.bootstrap_servers = bootstrap_servers
        self.topic = topic
        self.producer = None
        self.sensor_states = {}  # Track sensor states for realistic behavior
        
        # Sensor types and their characteristics
        self.sensor_types = {
            'temperature': {
                'unit': 'celsius',
                'normal_range': (18.0, 26.0),
                'critical_range': (-10.0, 50.0),
                'variance': 2.0,
                'anomaly_chance': 0.02
            },
            'humidity': {
                'unit': 'percent',
                'normal_range': (30.0, 70.0),
                'critical_range': (0.0, 100.0),
                'variance': 5.0,
                'anomaly_chance': 0.03
            },
            'pressure': {
                'unit': 'hPa',
                'normal_range': (1000.0, 1030.0),
                'critical_range': (950.0, 1080.0),
                'variance': 3.0,
                'anomaly_chance': 0.01
            },
            'light': {
                'unit': 'lux',
                'normal_range': (100.0, 1000.0),
                'critical_range': (0.0, 50000.0),
                'variance': 50.0,
                'anomaly_chance': 0.02
            },
            'motion': {
                'unit': 'boolean',
                'normal_range': (0, 1),
                'critical_range': (0, 1),
                'variance': 0.0,
                'anomaly_chance': 0.05
            },
            'air_quality': {
                'unit': 'ppm',
                'normal_range': (50.0, 150.0),
                'critical_range': (0.0, 1000.0),
                'variance': 10.0,
                'anomaly_chance': 0.04
            },
            'vibration': {
                'unit': 'mm/s',
                'normal_range': (0.5, 3.0),
                'critical_range': (0.0, 20.0),
                'variance': 0.3,
                'anomaly_chance': 0.03
            },
            'sound': {
                'unit': 'dB',
                'normal_range': (30.0, 60.0),
                'critical_range': (0.0, 120.0),
                'variance': 5.0,
                'anomaly_chance': 0.02
            },
            'power': {
                'unit': 'watts',
                'normal_range': (100.0, 500.0),
                'critical_range': (0.0, 2000.0),
                'variance': 25.0,
                'anomaly_chance': 0.03
            },
            'flow_rate': {
                'unit': 'liters/min',
                'normal_range': (10.0, 50.0),
                'critical_range': (0.0, 200.0),
                'variance': 5.0,
                'anomaly_chance': 0.02
            }
        }
        
        # Location types and their characteristics
        self.locations = {
            'warehouse_a': {'temperature': 22.0, 'humidity': 45.0, 'sensors': 15},
            'warehouse_b': {'temperature': 20.0, 'humidity': 50.0, 'sensors': 12},
            'office_floor_1': {'temperature': 23.0, 'humidity': 40.0, 'sensors': 8},
            'office_floor_2': {'temperature': 24.0, 'humidity': 42.0, 'sensors': 8},
            'production_line_1': {'temperature': 25.0, 'humidity': 35.0, 'sensors': 20},
            'production_line_2': {'temperature': 26.0, 'humidity': 38.0, 'sensors': 18},
            'server_room': {'temperature': 18.0, 'humidity': 30.0, 'sensors': 10},
            'loading_dock': {'temperature': 21.0, 'humidity': 60.0, 'sensors': 6},
            'cafeteria': {'temperature': 22.0, 'humidity': 45.0, 'sensors': 4},
            'parking_garage': {'temperature': 19.0, 'humidity': 55.0, 'sensors': 5}
        }
        
        # Device manufacturers and models
        self.device_info = {
            'manufacturers': ['SensorTech', 'IoTCorp', 'SmartDevices', 'TechSensors', 'ConnectedSolutions'],
            'models': ['ST-100', 'IoT-Pro', 'Smart-X1', 'TS-200', 'CS-Advanced'],
            'firmware_versions': ['1.0.1', '1.2.3', '2.0.0', '2.1.4', '3.0.2']
        }
        
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
    
    def generate_sensor_id(self, location: str, sensor_type: str) -> str:
        """Generate a unique sensor ID"""
        return f"{location}_{sensor_type}_{random.randint(1000, 9999)}"
    
    def get_or_create_sensor_state(self, sensor_id: str, sensor_type: str, location: str) -> Dict:
        """Get or create sensor state for realistic behavior"""
        if sensor_id not in self.sensor_states:
            sensor_config = self.sensor_types[sensor_type]
            location_config = self.locations[location]
            
            # Initialize sensor state
            if sensor_type == 'temperature':
                base_value = location_config.get('temperature', 22.0)
            elif sensor_type == 'humidity':
                base_value = location_config.get('humidity', 45.0)
            elif sensor_type == 'motion':
                base_value = 0
            else:
                base_value = random.uniform(*sensor_config['normal_range'])
            
            self.sensor_states[sensor_id] = {
                'last_value': base_value,
                'last_timestamp': datetime.now(timezone.utc),
                'battery_level': random.randint(20, 100),
                'calibration_date': datetime.now(timezone.utc) - timedelta(days=random.randint(1, 90)),
                'error_count': 0,
                'maintenance_due': datetime.now(timezone.utc) + timedelta(days=random.randint(30, 180)),
                'manufacturer': random.choice(self.device_info['manufacturers']),
                'model': random.choice(self.device_info['models']),
                'firmware_version': random.choice(self.device_info['firmware_versions']),
                'install_date': datetime.now(timezone.utc) - timedelta(days=random.randint(30, 365)),
                'anomaly_streak': 0
            }
        
        return self.sensor_states[sensor_id]
    
    def simulate_time_based_variation(self, base_value: float, sensor_type: str, current_time: datetime) -> float:
        """Apply time-based variations (daily cycles, etc.)"""
        hour = current_time.hour
        
        if sensor_type == 'temperature':
            # Daily temperature cycle
            daily_variation = 3.0 * math.sin((hour - 6) * math.pi / 12)
            return base_value + daily_variation
        
        elif sensor_type == 'light':
            # Light follows day/night cycle
            if 6 <= hour <= 18:
                return base_value * random.uniform(0.8, 1.2)
            else:
                return base_value * random.uniform(0.1, 0.3)
        
        elif sensor_type == 'motion':
            # More motion during business hours
            if 8 <= hour <= 17:
                return 1 if random.random() < 0.3 else 0
            else:
                return 1 if random.random() < 0.05 else 0
        
        elif sensor_type == 'power':
            # Power consumption varies by time
            if 8 <= hour <= 17:
                return base_value * random.uniform(0.9, 1.3)
            else:
                return base_value * random.uniform(0.3, 0.7)
        
        return base_value
    
    def generate_sensor_value(self, sensor_type: str, sensor_state: Dict, location: str) -> tuple:
        """Generate realistic sensor value with potential anomalies"""
        sensor_config = self.sensor_types[sensor_type]
        current_time = datetime.now(timezone.utc)
        
        # Get base value with time variations
        base_value = self.simulate_time_based_variation(
            sensor_state['last_value'], sensor_type, current_time
        )
        
        # Check for anomaly
        is_anomaly = random.random() < sensor_config['anomaly_chance']
        
        # If we're in an anomaly streak, continue it
        if sensor_state['anomaly_streak'] > 0:
            is_anomaly = True
            sensor_state['anomaly_streak'] -= 1
        elif is_anomaly:
            # Start new anomaly streak
            sensor_state['anomaly_streak'] = random.randint(1, 5)
        
        if sensor_type == 'motion':
            # Motion sensor (boolean)
            if is_anomaly:
                value = 1 if sensor_state['last_value'] == 0 else 0  # Unexpected motion/stillness
            else:
                value = self.simulate_time_based_variation(0, sensor_type, current_time)
        else:
            if is_anomaly:
                # Generate anomalous value
                critical_range = sensor_config['critical_range']
                normal_range = sensor_config['normal_range']
                
                # Anomalies can be:
                # 1. Outside normal range but within critical range
                # 2. Completely outside critical range (rare)
                if random.random() < 0.8:
                    # Moderate anomaly
                    if random.random() < 0.5:
                        value = random.uniform(critical_range[0], normal_range[0])
                    else:
                        value = random.uniform(normal_range[1], critical_range[1])
                else:
                    # Severe anomaly
                    if random.random() < 0.5:
                        value = critical_range[0] - random.uniform(0, 50)
                    else:
                        value = critical_range[1] + random.uniform(0, 50)
            else:
                # Generate normal value with slight drift from last value
                variance = sensor_config['variance']
                drift = random.uniform(-variance, variance)
                value = base_value + drift
                
                # Keep within normal range
                normal_range = sensor_config['normal_range']
                value = max(normal_range[0], min(normal_range[1], value))
        
        # Update sensor state
        sensor_state['last_value'] = value
        sensor_state['last_timestamp'] = current_time
        
        # Simulate battery drain
        if random.random() < 0.001:  # 0.1% chance per reading
            sensor_state['battery_level'] = max(0, sensor_state['battery_level'] - 1)
        
        # Simulate occasional errors
        if random.random() < 0.001:  # 0.1% chance
            sensor_state['error_count'] += 1
        
        return value, is_anomaly
    
    def calculate_quality_score(self, sensor_state: Dict, is_anomaly: bool) -> float:
        """Calculate data quality score (0.0 - 1.0)"""
        score = 1.0
        
        # Battery level impact
        if sensor_state['battery_level'] < 20:
            score -= 0.3
        elif sensor_state['battery_level'] < 50:
            score -= 0.1
        
        # Age since calibration
        days_since_calibration = (datetime.now(timezone.utc) - sensor_state['calibration_date']).days
        if days_since_calibration > 90:
            score -= 0.2
        elif days_since_calibration > 180:
            score -= 0.4
        
        # Error count impact
        score -= min(sensor_state['error_count'] * 0.02, 0.3)
        
        # Anomaly impact
        if is_anomaly:
            score -= 0.1
        
        return max(0.0, min(1.0, score))
    
    def generate_coordinates(self, location: str) -> Dict[str, float]:
        """Generate coordinates for the facility location"""
        # Simulate a large facility with different building coordinates
        base_coords = {
            'warehouse_a': {'lat': 40.7128, 'lon': -74.0060},
            'warehouse_b': {'lat': 40.7130, 'lon': -74.0058},
            'office_floor_1': {'lat': 40.7125, 'lon': -74.0065},
            'office_floor_2': {'lat': 40.7125, 'lon': -74.0065},
            'production_line_1': {'lat': 40.7135, 'lon': -74.0055},
            'production_line_2': {'lat': 40.7138, 'lon': -74.0052},
            'server_room': {'lat': 40.7120, 'lon': -74.0070},
            'loading_dock': {'lat': 40.7140, 'lon': -74.0050},
            'cafeteria': {'lat': 40.7122, 'lon': -74.0068},
            'parking_garage': {'lat': 40.7115, 'lon': -74.0075}
        }
        
        base = base_coords.get(location, {'lat': 40.7128, 'lon': -74.0060})
        return {
            'lat': round(base['lat'] + random.uniform(-0.001, 0.001), 6),
            'lon': round(base['lon'] + random.uniform(-0.001, 0.001), 6)
        }
    
    def generate_sensor_event(self) -> Dict:
        """Generate a realistic IoT sensor event"""
        # Select location and sensor type
        location = random.choice(list(self.locations.keys()))
        sensor_type = random.choice(list(self.sensor_types.keys()))
        
        # Generate or reuse sensor ID
        existing_sensors = [sid for sid in self.sensor_states.keys() 
                          if location in sid and sensor_type in sid]
        
        if existing_sensors and random.random() < 0.8:
            # 80% chance to reuse existing sensor
            sensor_id = random.choice(existing_sensors)
        else:
            # Create new sensor
            sensor_id = self.generate_sensor_id(location, sensor_type)
        
        # Get sensor state and generate value
        sensor_state = self.get_or_create_sensor_state(sensor_id, sensor_type, location)
        value, is_anomaly = self.generate_sensor_value(sensor_type, sensor_state, location)
        
        # Generate event
        event = {
            'timestamp': datetime.now(timezone.utc).isoformat(),
            'sensorId': sensor_id,
            'sensorType': sensor_type,
            'value': round(value, 3) if isinstance(value, float) else value,
            'unit': self.sensor_types[sensor_type]['unit'],
            'location': location,
            'coordinates': self.generate_coordinates(location),
            'batteryLevel': sensor_state['battery_level'],
            'signalStrength': random.randint(-90, -30),  # dBm
            'dataQuality': round(self.calculate_quality_score(sensor_state, is_anomaly), 3),
            'isAnomaly': is_anomaly,
            'deviceInfo': {
                'manufacturer': sensor_state['manufacturer'],
                'model': sensor_state['model'],
                'firmwareVersion': sensor_state['firmware_version'],
                'installDate': sensor_state['install_date'].isoformat(),
                'calibrationDate': sensor_state['calibration_date'].isoformat(),
                'maintenanceDue': sensor_state['maintenance_due'].isoformat()
            },
            'networkInfo': {
                'protocol': random.choice(['WiFi', 'LoRaWAN', 'Zigbee', 'Cellular']),
                'gatewayId': f"gateway_{random.randint(1, 10)}",
                'rssi': random.randint(-100, -40),
                'packetLoss': round(random.uniform(0, 5), 2)
            },
            'metadata': {
                'readingId': str(uuid.uuid4()),
                'sequenceNumber': random.randint(1000, 999999),
                'errorCount': sensor_state['error_count'],
                'samplingRate': random.choice([1, 5, 10, 30, 60]),  # seconds
                'processingTime': random.randint(1, 50)  # ms
            }
        }
        
        # Add sensor-specific metadata
        if sensor_type == 'temperature':
            event['metadata']['heatIndex'] = round(value + random.uniform(-2, 2), 2)
        elif sensor_type == 'vibration':
            event['metadata']['frequency'] = round(random.uniform(10, 1000), 2)  # Hz
        elif sensor_type == 'air_quality':
            event['metadata']['pollutant'] = random.choice(['CO2', 'PM2.5', 'VOC', 'NO2'])
        
        return event
    
    def send_event(self, event: Dict) -> bool:
        """Send sensor event to Kafka topic"""
        try:
            # Use location as partition key for locality
            future = self.producer.send(
                self.topic,
                key=event['location'],
                value=event
            )
            
            # Non-blocking send with callback
            future.add_callback(self._on_send_success)
            future.add_errback(self._on_send_error)
            
            return True
        except Exception as e:
            logger.error(f"Failed to send sensor event: {e}")
            return False
    
    def _on_send_success(self, record_metadata):
        """Callback for successful sends"""
        logger.debug(f"Sensor event sent to topic {record_metadata.topic} "
                    f"partition {record_metadata.partition} "
                    f"offset {record_metadata.offset}")
    
    def _on_send_error(self, exception):
        """Callback for send errors"""
        logger.error(f"Failed to send sensor event: {exception}")
    
    def produce_events(self, events_per_second: float = 20, duration_seconds: int = 60):
        """Produce sensor events at specified rate for given duration"""
        if not self.connect():
            return
        
        interval = 1.0 / events_per_second if events_per_second > 0 else 1.0
        end_time = time.time() + duration_seconds
        event_count = 0
        anomaly_count = 0
        
        logger.info(f"Starting to produce sensor events at {events_per_second} events/second for {duration_seconds} seconds")
        
        try:
            while time.time() < end_time:
                start_time = time.time()
                
                # Generate and send event
                event = self.generate_sensor_event()
                if self.send_event(event):
                    event_count += 1
                    if event['isAnomaly']:
                        anomaly_count += 1
                    
                    if event_count % 100 == 0:
                        anomaly_rate = (anomaly_count / event_count) * 100
                        logger.info(f"Produced {event_count} sensor events ({anomaly_count} anomalies, {anomaly_rate:.1f}%)")
                
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
            
            anomaly_rate = (anomaly_count / event_count) * 100 if event_count > 0 else 0
            logger.info(f"Total sensor events produced: {event_count}")
            logger.info(f"Anomalous readings: {anomaly_count} ({anomaly_rate:.1f}%)")
            logger.info(f"Active sensors: {len(self.sensor_states)}")
    
    def produce_continuous(self, events_per_second: float = 20):
        """Produce sensor events continuously until interrupted"""
        if not self.connect():
            return
        
        interval = 1.0 / events_per_second if events_per_second > 0 else 1.0
        event_count = 0
        anomaly_count = 0
        
        logger.info(f"Starting continuous sensor event production at {events_per_second} events/second")
        logger.info("Press Ctrl+C to stop")
        
        try:
            while True:
                start_time = time.time()
                
                # Generate and send event
                event = self.generate_sensor_event()
                if self.send_event(event):
                    event_count += 1
                    if event['isAnomaly']:
                        anomaly_count += 1
                    
                    if event_count % 100 == 0:
                        anomaly_rate = (anomaly_count / event_count) * 100
                        logger.info(f"Produced {event_count} sensor events ({anomaly_count} anomalies, {anomaly_rate:.1f}%)")
                
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
            
            anomaly_rate = (anomaly_count / event_count) * 100 if event_count > 0 else 0
            logger.info(f"Total sensor events produced: {event_count}")
            logger.info(f"Anomalous readings: {anomaly_count} ({anomaly_rate:.1f}%)")
            logger.info(f"Active sensors: {len(self.sensor_states)}")

def main():
    parser = argparse.ArgumentParser(description='IoT Sensor Data Producer')
    parser.add_argument('--bootstrap-servers', default='localhost:9092',
                       help='Kafka bootstrap servers')
    parser.add_argument('--topic', default='iot-sensor-events',
                       help='Kafka topic name')
    parser.add_argument('--rate', type=float, default=20,
                       help='Events per second')
    parser.add_argument('--duration', type=int, default=0,
                       help='Duration in seconds (0 for continuous)')
    
    args = parser.parse_args()
    
    producer = IoTSensorProducer(
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