package com.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.Offsets;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Advanced Complex Event Processing (CEP) job for real-time fraud detection.
 * 
 * This job implements multiple fraud detection patterns:
 * 1. Rapid successive transactions from same user (velocity-based)
 * 2. High-value transactions followed by multiple small transactions (structuring)
 * 3. Transactions from unusual locations (geographic anomaly)
 * 4. Multiple failed transactions followed by successful ones (testing attacks)
 * 5. Transactions outside normal business hours with high risk scores
 */
public class CEPFraudDetectionJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(CEPFraudDetectionJob.class);
    
    // Configuration constants
    private static final String KAFKA_BROKERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String INPUT_TOPIC = "transaction-events";
    private static final String OUTPUT_TOPIC = "fraud-alerts";
    private static final String JOB_NAME = "CEP-Fraud-Detection";
    
    // Fraud detection thresholds
    private static final int VELOCITY_TRANSACTION_COUNT = 5;
    private static final Duration VELOCITY_TIME_WINDOW = Duration.ofMinutes(2);
    private static final double HIGH_VALUE_THRESHOLD = 1000.0;
    private static final int STRUCTURING_SMALL_COUNT = 3;
    private static final double STRUCTURING_SMALL_THRESHOLD = 100.0;
    private static final int FAILED_ATTEMPTS_THRESHOLD = 3;
    private static final double HIGH_RISK_THRESHOLD = 0.7;
    
    public static void main(String[] args) throws Exception {
        // Set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(30000); // 30 seconds
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);
        env.getCheckpointConfig().setCheckpointTimeout(10000);
        
        // Configure parallelism
        env.setParallelism(4);
        
        LOG.info("Starting CEP Fraud Detection Job");
        
        // Create Kafka source for transaction events
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setTopics(INPUT_TOPIC)
                .setGroupId("cep-fraud-detection-group")
                .setStartingOffsets(Offsets.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        
        // Create Kafka sink for fraud alerts
        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(OUTPUT_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();
        
        // Parse transaction events from Kafka
        DataStream<TransactionEvent> transactions = env
                .fromSource(source, WatermarkStrategy
                        .<String>forBoundedOutOfOrderness(Duration.ofSeconds(20))
                        .withTimestampAssigner((event, timestamp) -> {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                JsonNode json = mapper.readTree(event);
                                return Instant.parse(json.get("timestamp").asText()).toEpochMilli();
                            } catch (Exception e) {
                                return System.currentTimeMillis();
                            }
                        }), "kafka-source")
                .map(new TransactionParser())
                .filter(tx -> tx != null);
        
        // Key transactions by user ID for pattern matching
        KeyedStream<TransactionEvent, String> keyedTransactions = transactions
                .keyBy(tx -> tx.userId);
        
        // Pattern 1: Velocity-based fraud detection
        DataStream<String> velocityFraudAlerts = detectVelocityFraud(keyedTransactions);
        
        // Pattern 2: Structuring fraud detection
        DataStream<String> structuringFraudAlerts = detectStructuringFraud(keyedTransactions);
        
        // Pattern 3: Geographic anomaly detection
        DataStream<String> geoAnomalyAlerts = detectGeographicAnomalies(keyedTransactions);
        
        // Pattern 4: Failed transaction pattern detection
        DataStream<String> failedPatternAlerts = detectFailedTransactionPatterns(keyedTransactions);
        
        // Pattern 5: Off-hours high-risk detection
        DataStream<String> offHoursAlerts = detectOffHoursHighRisk(transactions);
        
        // Combine all fraud alerts
        DataStream<String> allFraudAlerts = velocityFraudAlerts
                .union(structuringFraudAlerts)
                .union(geoAnomalyAlerts)
                .union(failedPatternAlerts)
                .union(offHoursAlerts);
        
        // Send fraud alerts to Kafka
        allFraudAlerts.sinkTo(sink);
        
        // Print fraud alerts for monitoring
        allFraudAlerts.print("FRAUD-ALERT");
        
        // Execute the job
        env.execute(JOB_NAME);
    }
    
    /**
     * Pattern 1: Detect rapid successive transactions (velocity-based fraud)
     */
    private static DataStream<String> detectVelocityFraud(KeyedStream<TransactionEvent, String> keyedTransactions) {
        // Pattern: 5 or more transactions within 2 minutes
        Pattern<TransactionEvent, ?> velocityPattern = Pattern
                .<TransactionEvent>begin("first")
                .where(new SimpleCondition<TransactionEvent>() {
                    @Override
                    public boolean filter(TransactionEvent tx) {
                        return "completed".equals(tx.status);
                    }
                })
                .followedBy("rapid")
                .where(new SimpleCondition<TransactionEvent>() {
                    @Override
                    public boolean filter(TransactionEvent tx) {
                        return "completed".equals(tx.status);
                    }
                })
                .times(VELOCITY_TRANSACTION_COUNT - 1)
                .within(VELOCITY_TIME_WINDOW);
        
        PatternStream<TransactionEvent> velocityPatternStream = CEP.pattern(
                keyedTransactions, velocityPattern);
        
        return velocityPatternStream.select(new PatternSelectFunction<TransactionEvent, String>() {
            @Override
            public String select(Map<String, List<TransactionEvent>> pattern) throws Exception {
                List<TransactionEvent> rapidTxs = pattern.get("rapid");
                TransactionEvent firstTx = pattern.get("first").get(0);
                TransactionEvent lastTx = rapidTxs.get(rapidTxs.size() - 1);
                
                double totalAmount = firstTx.amount + rapidTxs.stream()
                        .mapToDouble(tx -> tx.amount).sum();
                
                return createFraudAlert(
                        "VELOCITY_FRAUD",
                        firstTx.userId,
                        "Rapid successive transactions detected",
                        String.format("User made %d transactions totaling $%.2f in %d minutes",
                                rapidTxs.size() + 1, totalAmount, 
                                VELOCITY_TIME_WINDOW.toMinutes()),
                        0.9,
                        firstTx.transactionId
                );
            }
        });
    }
    
    /**
     * Pattern 2: Detect structuring fraud (high value followed by small amounts)
     */
    private static DataStream<String> detectStructuringFraud(KeyedStream<TransactionEvent, String> keyedTransactions) {
        // Pattern: High-value transaction followed by multiple small transactions
        Pattern<TransactionEvent, ?> structuringPattern = Pattern
                .<TransactionEvent>begin("highValue")
                .where(new SimpleCondition<TransactionEvent>() {
                    @Override
                    public boolean filter(TransactionEvent tx) {
                        return tx.amount >= HIGH_VALUE_THRESHOLD && "completed".equals(tx.status);
                    }
                })
                .followedBy("smallAmounts")
                .where(new SimpleCondition<TransactionEvent>() {
                    @Override
                    public boolean filter(TransactionEvent tx) {
                        return tx.amount <= STRUCTURING_SMALL_THRESHOLD && "completed".equals(tx.status);
                    }
                })
                .times(STRUCTURING_SMALL_COUNT)
                .within(Duration.ofMinutes(10));
        
        PatternStream<TransactionEvent> structuringPatternStream = CEP.pattern(
                keyedTransactions, structuringPattern);
        
        return structuringPatternStream.select(new PatternSelectFunction<TransactionEvent, String>() {
            @Override
            public String select(Map<String, List<TransactionEvent>> pattern) throws Exception {
                TransactionEvent highValueTx = pattern.get("highValue").get(0);
                List<TransactionEvent> smallTxs = pattern.get("smallAmounts");
                
                double smallTotal = smallTxs.stream().mapToDouble(tx -> tx.amount).sum();
                
                return createFraudAlert(
                        "STRUCTURING_FRAUD",
                        highValueTx.userId,
                        "Potential transaction structuring detected",
                        String.format("High-value transaction ($%.2f) followed by %d small transactions ($%.2f total)",
                                highValueTx.amount, smallTxs.size(), smallTotal),
                        0.8,
                        highValueTx.transactionId
                );
            }
        });
    }
    
    /**
     * Pattern 3: Detect geographic anomalies
     */
    private static DataStream<String> detectGeographicAnomalies(KeyedStream<TransactionEvent, String> keyedTransactions) {
        // Pattern: Transactions from different countries within short time
        Pattern<TransactionEvent, ?> geoPattern = Pattern
                .<TransactionEvent>begin("first")
                .where(new SimpleCondition<TransactionEvent>() {
                    @Override
                    public boolean filter(TransactionEvent tx) {
                        return "completed".equals(tx.status) && tx.country != null;
                    }
                })
                .followedBy("different")
                .where(new SimpleCondition<TransactionEvent>() {
                    @Override
                    public boolean filter(TransactionEvent tx) {
                        return "completed".equals(tx.status) && tx.country != null;
                    }
                })
                .within(Duration.ofMinutes(5));
        
        PatternStream<TransactionEvent> geoPatternStream = CEP.pattern(
                keyedTransactions, geoPattern);
        
        return geoPatternStream.select(new PatternSelectFunction<TransactionEvent, String>() {
            @Override
            public String select(Map<String, List<TransactionEvent>> pattern) throws Exception {
                TransactionEvent firstTx = pattern.get("first").get(0);
                TransactionEvent secondTx = pattern.get("different").get(0);
                
                // Check if transactions are from different countries
                if (!firstTx.country.equals(secondTx.country)) {
                    return createFraudAlert(
                            "GEOGRAPHIC_ANOMALY",
                            firstTx.userId,
                            "Suspicious geographic activity detected",
                            String.format("Transactions from different countries: %s and %s within 5 minutes",
                                    firstTx.country, secondTx.country),
                            0.7,
                            firstTx.transactionId
                    );
                }
                return null; // No alert if same country
            }
        }).filter(alert -> alert != null);
    }
    
    /**
     * Pattern 4: Detect failed transaction patterns (testing attacks)
     */
    private static DataStream<String> detectFailedTransactionPatterns(KeyedStream<TransactionEvent, String> keyedTransactions) {
        // Pattern: Multiple failed transactions followed by successful one
        Pattern<TransactionEvent, ?> failedPattern = Pattern
                .<TransactionEvent>begin("failed")
                .where(new SimpleCondition<TransactionEvent>() {
                    @Override
                    public boolean filter(TransactionEvent tx) {
                        return "failed".equals(tx.status);
                    }
                })
                .times(FAILED_ATTEMPTS_THRESHOLD)
                .followedBy("success")
                .where(new SimpleCondition<TransactionEvent>() {
                    @Override
                    public boolean filter(TransactionEvent tx) {
                        return "completed".equals(tx.status);
                    }
                })
                .within(Duration.ofMinutes(5));
        
        PatternStream<TransactionEvent> failedPatternStream = CEP.pattern(
                keyedTransactions, failedPattern);
        
        return failedPatternStream.select(new PatternSelectFunction<TransactionEvent, String>() {
            @Override
            public String select(Map<String, List<TransactionEvent>> pattern) throws Exception {
                List<TransactionEvent> failedTxs = pattern.get("failed");
                TransactionEvent successTx = pattern.get("success").get(0);
                
                return createFraudAlert(
                        "TESTING_ATTACK",
                        successTx.userId,
                        "Potential card testing attack detected",
                        String.format("%d failed transactions followed by successful transaction of $%.2f",
                                failedTxs.size(), successTx.amount),
                        0.85,
                        successTx.transactionId
                );
            }
        });
    }
    
    /**
     * Pattern 5: Detect off-hours high-risk transactions
     */
    private static DataStream<String> detectOffHoursHighRisk(DataStream<TransactionEvent> transactions) {
        return transactions
                .filter(tx -> isOffHours(tx.timestamp) && 
                             tx.riskScore >= HIGH_RISK_THRESHOLD && 
                             "completed".equals(tx.status))
                .map(tx -> createFraudAlert(
                        "OFF_HOURS_HIGH_RISK",
                        tx.userId,
                        "High-risk transaction during off-hours",
                        String.format("Transaction of $%.2f with risk score %.2f at %s",
                                tx.amount, tx.riskScore, 
                                Instant.ofEpochMilli(tx.timestamp).toString()),
                        tx.riskScore,
                        tx.transactionId
                ));
    }
    
    /**
     * Helper method to check if timestamp is during off-hours (10 PM - 6 AM)
     */
    private static boolean isOffHours(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        int hour = instant.atZone(java.time.ZoneId.systemDefault()).getHour();
        return hour >= 22 || hour <= 6;
    }
    
    /**
     * Helper method to create standardized fraud alert JSON
     */
    private static String createFraudAlert(String alertType, String userId, String title, 
                                         String description, double severity, String transactionId) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode alert = mapper.createObjectNode();
            
            alert.put("alertId", java.util.UUID.randomUUID().toString());
            alert.put("timestamp", Instant.now().toString());
            alert.put("alertType", alertType);
            alert.put("userId", userId);
            alert.put("transactionId", transactionId);
            alert.put("title", title);
            alert.put("description", description);
            alert.put("severity", severity);
            alert.put("status", "ACTIVE");
            alert.put("source", "CEP-FRAUD-DETECTION");
            
            return mapper.writeValueAsString(alert);
        } catch (Exception e) {
            LOG.error("Error creating fraud alert", e);
            return String.format("{\"error\": \"Failed to create alert\", \"type\": \"%s\"}", alertType);
        }
    }
    
    /**
     * Parser to convert Kafka JSON messages to TransactionEvent objects
     */
    public static class TransactionParser implements MapFunction<String, TransactionEvent> {
        private static final ObjectMapper mapper = new ObjectMapper();
        
        @Override
        public TransactionEvent map(String value) throws Exception {
            try {
                JsonNode json = mapper.readTree(value);
                
                TransactionEvent tx = new TransactionEvent();
                tx.transactionId = json.get("transactionId").asText();
                tx.userId = json.get("userId").asText();
                tx.amount = json.get("amount").asDouble();
                tx.currency = json.get("currency").asText();
                tx.status = json.get("status").asText();
                tx.timestamp = Instant.parse(json.get("timestamp").asText()).toEpochMilli();
                tx.riskScore = json.has("riskScore") ? json.get("riskScore").asDouble() : 0.0;
                tx.country = json.has("country") ? json.get("country").asText() : "UNKNOWN";
                tx.merchantId = json.has("merchantId") ? json.get("merchantId").asText() : "UNKNOWN";
                
                return tx;
            } catch (Exception e) {
                LOG.warn("Failed to parse transaction: {}", value, e);
                return null;
            }
        }
    }
    
    /**
     * Transaction event POJO
     */
    public static class TransactionEvent {
        public String transactionId;
        public String userId;
        public double amount;
        public String currency;
        public String status;
        public long timestamp;
        public double riskScore;
        public String country;
        public String merchantId;
        
        public TransactionEvent() {}
        
        @Override
        public String toString() {
            return String.format("TransactionEvent{id='%s', user='%s', amount=%.2f, status='%s', risk=%.2f}", 
                    transactionId, userId, amount, status, riskScore);
        }
    }
}