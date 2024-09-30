package com.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.elasticsearch.sink.Elasticsearch7SinkBuilder;
import org.apache.flink.connector.elasticsearch.sink.ElasticsearchSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Flink job for sinking processed events to Elasticsearch
 * Demonstrates Elasticsearch connector and data enrichment
 */
public class ElasticsearchSinkJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchSinkJob.class);
    
    // Configuration constants
    private static final String KAFKA_BROKERS = "kafka1:29092,kafka2:29093,kafka3:29094";
    private static final String ELASTICSEARCH_HOST = "elasticsearch";
    private static final int ELASTICSEARCH_PORT = 9200;
    private static final String JOB_NAME = "ElasticsearchSinkJob";
    
    // Topics to sink
    private static final String USER_EVENTS_TOPIC = "user-events";
    private static final String TRANSACTION_EVENTS_TOPIC = "transaction-events";
    private static final String IOT_SENSOR_EVENTS_TOPIC = "iot-sensor-events";
    
    // Elasticsearch indices
    private static final String USER_EVENTS_INDEX = "user-events";
    private static final String TRANSACTION_EVENTS_INDEX = "transaction-events";
    private static final String IOT_SENSOR_EVENTS_INDEX = "iot-sensor-events";
    
    public static void main(String[] args) throws Exception {
        
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(60000); // Checkpoint every minute
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000);
        env.getCheckpointConfig().setCheckpointTimeout(300000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        
        // Set parallelism
        env.setParallelism(4);
        
        LOG.info("Starting Elasticsearch Sink Job");
        
        // Create Elasticsearch sink
        ElasticsearchSink<String> elasticsearchSink = createElasticsearchSink();
        
        // Process User Events
        DataStream<String> userEventsStream = createKafkaSource(env, USER_EVENTS_TOPIC, "user-events-es-group");
        DataStream<String> enrichedUserEvents = userEventsStream
                .map(event -> enrichUserEvent(event))
                .name("Enrich User Events");
        
        enrichedUserEvents.sinkTo(elasticsearchSink).name("User Events to Elasticsearch");
        
        // Process Transaction Events
        DataStream<String> transactionEventsStream = createKafkaSource(env, TRANSACTION_EVENTS_TOPIC, "transaction-events-es-group");
        DataStream<String> enrichedTransactionEvents = transactionEventsStream
                .map(event -> enrichTransactionEvent(event))
                .name("Enrich Transaction Events");
        
        enrichedTransactionEvents.sinkTo(elasticsearchSink).name("Transaction Events to Elasticsearch");
        
        // Process IoT Sensor Events
        DataStream<String> iotSensorEventsStream = createKafkaSource(env, IOT_SENSOR_EVENTS_TOPIC, "iot-sensor-events-es-group");
        DataStream<String> enrichedIoTEvents = iotSensorEventsStream
                .map(event -> enrichIoTSensorEvent(event))
                .name("Enrich IoT Sensor Events");
        
        enrichedIoTEvents.sinkTo(elasticsearchSink).name("IoT Sensor Events to Elasticsearch");
        
        // Execute the job
        env.execute(JOB_NAME);
    }
    
    private static DataStream<String> createKafkaSource(StreamExecutionEnvironment env, String topic, String groupId) {
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        
        return env.fromSource(source, 
                org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), 
                "Kafka Source: " + topic);
    }
    
    private static ElasticsearchSink<String> createElasticsearchSink() {
        return new Elasticsearch7SinkBuilder<String>()
                .setBulkFlushMaxActions(1000) // Flush after 1000 documents
                .setBulkFlushMaxSizeMb(5)     // Flush after 5MB
                .setBulkFlushInterval(5000)   // Flush every 5 seconds
                .setHosts(new HttpHost(ELASTICSEARCH_HOST, ELASTICSEARCH_PORT, "http"))
                .setEmitter((element, context, indexer) -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
                        JsonNode jsonNode = mapper.readTree(element);
                        
                        // Determine index based on event type
                        String indexName = determineIndexName(jsonNode);
                        
                        // Create index request
                        IndexRequest indexRequest = Requests.indexRequest()
                                .index(indexName)
                                .source(element, XContentType.JSON);
                        
                        // Set document ID if available
                        if (jsonNode.has("transactionId")) {
                            indexRequest.id(jsonNode.get("transactionId").asText());
                        } else if (jsonNode.has("sensorId") && jsonNode.has("timestamp")) {
                            // For sensor events, use sensorId + timestamp as ID
                            String id = jsonNode.get("sensorId").asText() + "_" + 
                                       jsonNode.get("timestamp").asText().replaceAll("[^a-zA-Z0-9]", "_");
                            indexRequest.id(id);
                        } else if (jsonNode.has("userId") && jsonNode.has("timestamp")) {
                            // For user events, use userId + timestamp as ID
                            String id = jsonNode.get("userId").asText() + "_" + 
                                       jsonNode.get("timestamp").asText().replaceAll("[^a-zA-Z0-9]", "_");
                            indexRequest.id(id);
                        }
                        
                        indexer.add(indexRequest);
                        
                    } catch (Exception e) {
                        LOG.error("Failed to create index request for element: {}", element, e);
                    }
                })
                .build();
    }
    
    private static String determineIndexName(JsonNode jsonNode) {
        // Determine index based on event structure
        if (jsonNode.has("transactionId")) {
            return TRANSACTION_EVENTS_INDEX;
        } else if (jsonNode.has("sensorId")) {
            return IOT_SENSOR_EVENTS_INDEX;
        } else {
            return USER_EVENTS_INDEX;
        }
    }
    
    private static String enrichUserEvent(String eventJson) {
        try {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            ObjectNode jsonNode = (ObjectNode) mapper.readTree(eventJson);
            
            // Add processing metadata
            jsonNode.put("processedAt", Instant.now().toString());
            jsonNode.put("eventSource", "user-events");
            
            // Parse and enrich timestamp
            if (jsonNode.has("timestamp")) {
                String timestamp = jsonNode.get("timestamp").asText();
                Instant instant = Instant.parse(timestamp);
                
                // Add time-based fields for easier querying
                LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                jsonNode.put("date", dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE));
                jsonNode.put("hour", dateTime.getHour());
                jsonNode.put("dayOfWeek", dateTime.getDayOfWeek().getValue());
                jsonNode.put("month", dateTime.getMonthValue());
                jsonNode.put("year", dateTime.getYear());
            }
            
            // Add event categorization
            if (jsonNode.has("eventType")) {
                String eventType = jsonNode.get("eventType").asText();
                jsonNode.put("eventCategory", categorizeUserEvent(eventType));
            }
            
            // Add location enrichment
            if (jsonNode.has("country")) {
                String country = jsonNode.get("country").asText();
                jsonNode.put("region", getRegion(country));
                jsonNode.put("timezone", getTimezone(country));
            }
            
            return mapper.writeValueAsString(jsonNode);
            
        } catch (Exception e) {
            LOG.error("Failed to enrich user event: {}", eventJson, e);
            return eventJson; // Return original if enrichment fails
        }
    }
    
    private static String enrichTransactionEvent(String eventJson) {
        try {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            ObjectNode jsonNode = (ObjectNode) mapper.readTree(eventJson);
            
            // Add processing metadata
            jsonNode.put("processedAt", Instant.now().toString());
            jsonNode.put("eventSource", "transaction-events");
            
            // Parse and enrich timestamp
            if (jsonNode.has("timestamp")) {
                String timestamp = jsonNode.get("timestamp").asText();
                Instant instant = Instant.parse(timestamp);
                
                LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                jsonNode.put("date", dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE));
                jsonNode.put("hour", dateTime.getHour());
                jsonNode.put("dayOfWeek", dateTime.getDayOfWeek().getValue());
                jsonNode.put("month", dateTime.getMonthValue());
                jsonNode.put("year", dateTime.getYear());
            }
            
            // Add risk categorization
            if (jsonNode.has("riskScore")) {
                double riskScore = jsonNode.get("riskScore").asDouble();
                jsonNode.put("riskCategory", categorizeRisk(riskScore));
            }
            
            // Add amount categorization
            if (jsonNode.has("amount")) {
                double amount = jsonNode.get("amount").asDouble();
                jsonNode.put("amountCategory", categorizeAmount(amount));
            }
            
            // Add merchant category enrichment
            if (jsonNode.has("merchantCategory")) {
                String merchantCategory = jsonNode.get("merchantCategory").asText();
                jsonNode.put("merchantType", getMerchantType(merchantCategory));
            }
            
            return mapper.writeValueAsString(jsonNode);
            
        } catch (Exception e) {
            LOG.error("Failed to enrich transaction event: {}", eventJson, e);
            return eventJson;
        }
    }
    
    private static String enrichIoTSensorEvent(String eventJson) {
        try {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            ObjectNode jsonNode = (ObjectNode) mapper.readTree(eventJson);
            
            // Add processing metadata
            jsonNode.put("processedAt", Instant.now().toString());
            jsonNode.put("eventSource", "iot-sensor-events");
            
            // Parse and enrich timestamp
            if (jsonNode.has("timestamp")) {
                String timestamp = jsonNode.get("timestamp").asText();
                Instant instant = Instant.parse(timestamp);
                
                LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                jsonNode.put("date", dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE));
                jsonNode.put("hour", dateTime.getHour());
                jsonNode.put("dayOfWeek", dateTime.getDayOfWeek().getValue());
                jsonNode.put("month", dateTime.getMonthValue());
                jsonNode.put("year", dateTime.getYear());
            }
            
            // Add sensor categorization
            if (jsonNode.has("sensorType")) {
                String sensorType = jsonNode.get("sensorType").asText();
                jsonNode.put("sensorCategory", categorizeSensor(sensorType));
            }
            
            // Add value range analysis
            if (jsonNode.has("value") && jsonNode.has("sensorType")) {
                double value = jsonNode.get("value").asDouble();
                String sensorType = jsonNode.get("sensorType").asText();
                jsonNode.put("valueRange", categorizeValue(value, sensorType));
            }
            
            // Add location categorization
            if (jsonNode.has("location")) {
                String location = jsonNode.get("location").asText();
                jsonNode.put("locationCategory", categorizeLocation(location));
            }
            
            // Add battery status
            if (jsonNode.has("batteryLevel")) {
                int batteryLevel = jsonNode.get("batteryLevel").asInt();
                jsonNode.put("batteryStatus", categorizeBattery(batteryLevel));
            }
            
            return mapper.writeValueAsString(jsonNode);
            
        } catch (Exception e) {
            LOG.error("Failed to enrich IoT sensor event: {}", eventJson, e);
            return eventJson;
        }
    }
    
    // Helper methods for categorization
    private static String categorizeUserEvent(String eventType) {
        switch (eventType.toLowerCase()) {
            case "page_view":
            case "click":
            case "scroll":
                return "engagement";
            case "search":
                return "search";
            case "add_to_cart":
            case "remove_from_cart":
            case "purchase":
                return "commerce";
            case "login":
            case "logout":
            case "signup":
                return "authentication";
            default:
                return "other";
        }
    }
    
    private static String categorizeRisk(double riskScore) {
        if (riskScore >= 0.8) {
            return "high";
        } else if (riskScore >= 0.5) {
            return "medium";
        } else if (riskScore >= 0.2) {
            return "low";
        } else {
            return "very_low";
        }
    }
    
    private static String categorizeAmount(double amount) {
        if (amount >= 1000) {
            return "large";
        } else if (amount >= 100) {
            return "medium";
        } else if (amount >= 10) {
            return "small";
        } else {
            return "micro";
        }
    }
    
    private static String getMerchantType(String merchantCategory) {
        switch (merchantCategory.toLowerCase()) {
            case "grocery":
            case "restaurant":
                return "food_beverage";
            case "gas_station":
                return "automotive";
            case "retail":
            case "online":
            case "electronics":
                return "retail";
            case "hotel":
            case "airline":
                return "travel";
            case "pharmacy":
                return "healthcare";
            default:
                return "other";
        }
    }
    
    private static String categorizeSensor(String sensorType) {
        switch (sensorType.toLowerCase()) {
            case "temperature":
            case "humidity":
            case "pressure":
                return "environmental";
            case "motion":
            case "sound":
                return "security";
            case "vibration":
            case "power":
            case "flow_rate":
                return "industrial";
            case "air_quality":
            case "light":
                return "comfort";
            default:
                return "other";
        }
    }
    
    private static String categorizeValue(double value, String sensorType) {
        // Define normal ranges for different sensor types
        switch (sensorType.toLowerCase()) {
            case "temperature":
                if (value < 10) return "very_low";
                if (value < 18) return "low";
                if (value <= 26) return "normal";
                if (value <= 35) return "high";
                return "very_high";
            case "humidity":
                if (value < 20) return "very_low";
                if (value < 30) return "low";
                if (value <= 70) return "normal";
                if (value <= 85) return "high";
                return "very_high";
            default:
                return "unknown";
        }
    }
    
    private static String categorizeLocation(String location) {
        if (location.contains("warehouse")) {
            return "storage";
        } else if (location.contains("office")) {
            return "office";
        } else if (location.contains("production")) {
            return "manufacturing";
        } else if (location.contains("server")) {
            return "it_infrastructure";
        } else {
            return "other";
        }
    }
    
    private static String categorizeBattery(int batteryLevel) {
        if (batteryLevel >= 80) {
            return "excellent";
        } else if (batteryLevel >= 50) {
            return "good";
        } else if (batteryLevel >= 20) {
            return "low";
        } else {
            return "critical";
        }
    }
    
    private static String getRegion(String country) {
        switch (country) {
            case "US":
            case "CA":
            case "MX":
                return "North America";
            case "UK":
            case "DE":
            case "FR":
                return "Europe";
            case "AU":
                return "Oceania";
            case "JP":
            case "IN":
                return "Asia";
            case "BR":
                return "South America";
            default:
                return "Other";
        }
    }
    
    private static String getTimezone(String country) {
        switch (country) {
            case "US":
                return "America/New_York";
            case "UK":
                return "Europe/London";
            case "DE":
                return "Europe/Berlin";
            case "JP":
                return "Asia/Tokyo";
            case "AU":
                return "Australia/Sydney";
            default:
                return "UTC";
        }
    }
}