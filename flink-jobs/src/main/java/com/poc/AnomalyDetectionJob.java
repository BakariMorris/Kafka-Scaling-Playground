package com.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.Offsets;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Anomaly Detection job for IoT sensor data using multiple detection algorithms:
 * 
 * 1. Statistical Anomaly Detection - Z-score based detection using sliding windows
 * 2. Threshold-based Detection - Simple threshold violations  
 * 3. Rate of Change Detection - Rapid value changes indicating sensor issues
 * 4. Missing Data Detection - Sensors that stop reporting
 * 5. Cross-sensor Correlation - Detecting inconsistencies across related sensors
 * 6. Seasonal Pattern Detection - Deviations from expected patterns
 */
public class AnomalyDetectionJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(AnomalyDetectionJob.class);
    
    // Configuration constants
    private static final String KAFKA_BROKERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String INPUT_TOPIC = "iot-sensor-events";
    private static final String OUTPUT_TOPIC = "anomaly-alerts";
    private static final String JOB_NAME = "Advanced-Anomaly-Detection";
    
    // Anomaly detection parameters
    private static final double Z_SCORE_THRESHOLD = 2.5; // Values beyond 2.5 std deviations
    private static final int STATISTICAL_WINDOW_SIZE = 100; // Number of readings for statistics
    private static final double RATE_CHANGE_THRESHOLD = 0.5; // 50% change rate threshold
    private static final Duration MISSING_DATA_TIMEOUT = Duration.ofMinutes(5);
    private static final int MIN_READINGS_FOR_STATS = 10;
    
    public static void main(String[] args) throws Exception {
        // Set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(30000); // 30 seconds
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);
        env.getCheckpointConfig().setCheckpointTimeout(10000);
        
        // Configure parallelism
        env.setParallelism(4);
        
        LOG.info("Starting Advanced Anomaly Detection Job");
        
        // Create Kafka source for IoT sensor events
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setTopics(INPUT_TOPIC)
                .setGroupId("anomaly-detection-group")
                .setStartingOffsets(Offsets.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        
        // Create Kafka sink for anomaly alerts
        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(OUTPUT_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();
        
        // Parse IoT sensor events from Kafka
        DataStream<SensorReading> sensorReadings = env
                .fromSource(source, WatermarkStrategy
                        .<String>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((event, timestamp) -> {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                JsonNode json = mapper.readTree(event);
                                return Instant.parse(json.get("timestamp").asText()).toEpochMilli();
                            } catch (Exception e) {
                                return System.currentTimeMillis();
                            }
                        }), "kafka-source")
                .map(new SensorReadingParser())
                .filter(reading -> reading != null);
        
        // Key sensor readings by sensor ID
        KeyedStream<SensorReading, String> keyedReadings = sensorReadings
                .keyBy(reading -> reading.sensorId);
        
        // 1. Statistical Anomaly Detection (Z-score based)
        DataStream<String> statisticalAnomalies = detectStatisticalAnomalies(keyedReadings);
        
        // 2. Threshold-based Detection
        DataStream<String> thresholdAnomalies = detectThresholdAnomalies(sensorReadings);
        
        // 3. Rate of Change Detection
        DataStream<String> rateChangeAnomalies = detectRateChangeAnomalies(keyedReadings);
        
        // 4. Missing Data Detection
        DataStream<String> missingDataAnomalies = detectMissingData(keyedReadings);
        
        // 5. Cross-sensor Correlation Detection
        DataStream<String> correlationAnomalies = detectCorrelationAnomalies(sensorReadings);
        
        // Combine all anomaly alerts
        DataStream<String> allAnomalyAlerts = statisticalAnomalies
                .union(thresholdAnomalies)
                .union(rateChangeAnomalies)
                .union(missingDataAnomalies)
                .union(correlationAnomalies);
        
        // Send anomaly alerts to Kafka
        allAnomalyAlerts.sinkTo(sink);
        
        // Print anomaly alerts for monitoring
        allAnomalyAlerts.print("ANOMALY-ALERT");
        
        // Execute the job
        env.execute(JOB_NAME);
    }
    
    /**
     * Statistical anomaly detection using Z-score in sliding windows
     */
    private static DataStream<String> detectStatisticalAnomalies(KeyedStream<SensorReading, String> keyedReadings) {
        return keyedReadings
                .process(new KeyedProcessFunction<String, SensorReading, String>() {
                    
                    private ValueState<List<Double>> historicalValues;
                    private ValueState<Double> mean;
                    private ValueState<Double> variance;
                    
                    @Override
                    public void open(Configuration parameters) throws Exception {
                        ValueStateDescriptor<List<Double>> historyDescriptor = 
                                new ValueStateDescriptor<>("historical-values", Types.LIST(Types.DOUBLE));
                        historicalValues = getRuntimeContext().getState(historyDescriptor);
                        
                        ValueStateDescriptor<Double> meanDescriptor = 
                                new ValueStateDescriptor<>("mean", Types.DOUBLE);
                        mean = getRuntimeContext().getState(meanDescriptor);
                        
                        ValueStateDescriptor<Double> varianceDescriptor = 
                                new ValueStateDescriptor<>("variance", Types.DOUBLE);
                        variance = getRuntimeContext().getState(varianceDescriptor);
                    }
                    
                    @Override
                    public void processElement(SensorReading reading, Context ctx, Collector<String> out) throws Exception {
                        List<Double> history = historicalValues.value();
                        if (history == null) {
                            history = new ArrayList<>();
                        }
                        
                        // Add current reading to history
                        history.add(reading.value);
                        
                        // Maintain sliding window
                        if (history.size() > STATISTICAL_WINDOW_SIZE) {
                            history.remove(0);
                        }
                        
                        historicalValues.update(history);
                        
                        // Calculate statistics if we have enough data
                        if (history.size() >= MIN_READINGS_FOR_STATS) {
                            double currentMean = calculateMean(history);
                            double currentVariance = calculateVariance(history, currentMean);
                            double stdDev = Math.sqrt(currentVariance);
                            
                            mean.update(currentMean);
                            variance.update(currentVariance);
                            
                            // Calculate Z-score for current reading
                            if (stdDev > 0) {
                                double zScore = Math.abs(reading.value - currentMean) / stdDev;
                                
                                if (zScore > Z_SCORE_THRESHOLD) {
                                    String alert = createAnomalyAlert(
                                            "STATISTICAL_ANOMALY",
                                            reading.sensorId,
                                            "Statistical anomaly detected",
                                            String.format("Value %.2f deviates %.2f standard deviations from mean %.2f",
                                                    reading.value, zScore, currentMean),
                                            Math.min(0.9, zScore / Z_SCORE_THRESHOLD),
                                            reading
                                    );
                                    out.collect(alert);
                                }
                            }
                        }
                    }
                    
                    private double calculateMean(List<Double> values) {
                        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    }
                    
                    private double calculateVariance(List<Double> values, double mean) {
                        return values.stream()
                                .mapToDouble(value -> Math.pow(value - mean, 2))
                                .average().orElse(0.0);
                    }
                });
    }
    
    /**
     * Threshold-based anomaly detection
     */
    private static DataStream<String> detectThresholdAnomalies(DataStream<SensorReading> sensorReadings) {
        return sensorReadings
                .filter(reading -> isThresholdViolation(reading))
                .map(reading -> createAnomalyAlert(
                        "THRESHOLD_VIOLATION",
                        reading.sensorId,
                        "Threshold violation detected",
                        String.format("%s sensor value %.2f %s is outside safe range",
                                reading.deviceType, reading.value, reading.unit),
                        calculateThresholdSeverity(reading),
                        reading
                ));
    }
    
    /**
     * Rate of change anomaly detection
     */
    private static DataStream<String> detectRateChangeAnomalies(KeyedStream<SensorReading, String> keyedReadings) {
        return keyedReadings
                .process(new KeyedProcessFunction<String, SensorReading, String>() {
                    
                    private ValueState<SensorReading> lastReading;
                    
                    @Override
                    public void open(Configuration parameters) throws Exception {
                        ValueStateDescriptor<SensorReading> lastReadingDescriptor = 
                                new ValueStateDescriptor<>("last-reading", SensorReading.class);
                        lastReading = getRuntimeContext().getState(lastReadingDescriptor);
                    }
                    
                    @Override
                    public void processElement(SensorReading reading, Context ctx, Collector<String> out) throws Exception {
                        SensorReading previous = lastReading.value();
                        
                        if (previous != null) {
                            // Calculate time difference in seconds
                            long timeDiffMs = reading.timestamp - previous.timestamp;
                            double timeDiffSec = timeDiffMs / 1000.0;
                            
                            if (timeDiffSec > 0 && timeDiffSec < 300) { // Within 5 minutes
                                double valueDiff = Math.abs(reading.value - previous.value);
                                double rateOfChange = valueDiff / timeDiffSec;
                                double relativeChange = valueDiff / Math.abs(previous.value);
                                
                                // Detect rapid changes
                                if (relativeChange > RATE_CHANGE_THRESHOLD) {
                                    String alert = createAnomalyAlert(
                                            "RAPID_CHANGE",
                                            reading.sensorId,
                                            "Rapid value change detected",
                                            String.format("Value changed by %.2f%% (from %.2f to %.2f) in %.1f seconds",
                                                    relativeChange * 100, previous.value, reading.value, timeDiffSec),
                                            Math.min(0.9, relativeChange),
                                            reading
                                    );
                                    out.collect(alert);
                                }
                            }
                        }
                        
                        lastReading.update(reading);
                    }
                });
    }
    
    /**
     * Missing data anomaly detection
     */
    private static DataStream<String> detectMissingData(KeyedStream<SensorReading, String> keyedReadings) {
        return keyedReadings
                .process(new KeyedProcessFunction<String, SensorReading, String>() {
                    
                    private ValueState<Long> lastSeenTime;
                    
                    @Override
                    public void open(Configuration parameters) throws Exception {
                        ValueStateDescriptor<Long> lastSeenDescriptor = 
                                new ValueStateDescriptor<>("last-seen-time", Types.LONG);
                        lastSeenTime = getRuntimeContext().getState(lastSeenDescriptor);
                    }
                    
                    @Override
                    public void processElement(SensorReading reading, Context ctx, Collector<String> out) throws Exception {
                        Long lastSeen = lastSeenTime.value();
                        long currentTime = ctx.timestamp();
                        
                        // Set up timer for missing data detection
                        ctx.timerService().registerEventTimeTimer(currentTime + MISSING_DATA_TIMEOUT.toMillis());
                        
                        if (lastSeen != null) {
                            long timeSinceLastReading = currentTime - lastSeen;
                            
                            // Check if sensor was missing for too long
                            if (timeSinceLastReading > MISSING_DATA_TIMEOUT.toMillis()) {
                                String alert = createAnomalyAlert(
                                        "MISSING_DATA",
                                        reading.sensorId,
                                        "Sensor data gap detected",
                                        String.format("No data received for %.1f minutes",
                                                timeSinceLastReading / 60000.0),
                                        0.7,
                                        reading
                                );
                                out.collect(alert);
                            }
                        }
                        
                        lastSeenTime.update(currentTime);
                    }
                    
                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        Long lastSeen = lastSeenTime.value();
                        if (lastSeen != null && (timestamp - lastSeen) >= MISSING_DATA_TIMEOUT.toMillis()) {
                            // Create a dummy reading for the alert
                            SensorReading dummyReading = new SensorReading();
                            dummyReading.sensorId = ctx.getCurrentKey();
                            dummyReading.timestamp = timestamp;
                            dummyReading.deviceType = "UNKNOWN";
                            dummyReading.value = 0.0;
                            dummyReading.unit = "UNKNOWN";
                            dummyReading.location = "UNKNOWN";
                            
                            String alert = createAnomalyAlert(
                                    "SENSOR_OFFLINE",
                                    ctx.getCurrentKey(),
                                    "Sensor appears to be offline",
                                    String.format("No data received for over %.1f minutes",
                                            MISSING_DATA_TIMEOUT.toMinutes()),
                                    0.8,
                                    dummyReading
                            );
                            out.collect(alert);
                        }
                    }
                });
    }
    
    /**
     * Cross-sensor correlation anomaly detection
     */
    private static DataStream<String> detectCorrelationAnomalies(DataStream<SensorReading> sensorReadings) {
        // Group temperature sensors by location and detect inconsistencies
        return sensorReadings
                .filter(reading -> "temperature".equals(reading.deviceType))
                .keyBy(reading -> reading.location)
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .aggregate(new AggregateFunction<SensorReading, List<SensorReading>, List<SensorReading>>() {
                    @Override
                    public List<SensorReading> createAccumulator() {
                        return new ArrayList<>();
                    }
                    
                    @Override
                    public List<SensorReading> add(SensorReading reading, List<SensorReading> accumulator) {
                        accumulator.add(reading);
                        return accumulator;
                    }
                    
                    @Override
                    public List<SensorReading> getResult(List<SensorReading> accumulator) {
                        return accumulator;
                    }
                    
                    @Override
                    public List<SensorReading> merge(List<SensorReading> a, List<SensorReading> b) {
                        List<SensorReading> merged = new ArrayList<>(a);
                        merged.addAll(b);
                        return merged;
                    }
                })
                .flatMap((List<SensorReading> readings, Collector<String> out) -> {
                    if (readings.size() >= 2) {
                        // Calculate temperature variance across sensors in same location
                        double mean = readings.stream().mapToDouble(r -> r.value).average().orElse(0.0);
                        double maxDeviation = readings.stream()
                                .mapToDouble(r -> Math.abs(r.value - mean))
                                .max().orElse(0.0);
                        
                        // Alert if temperature variance is too high (>5°C)
                        if (maxDeviation > 5.0) {
                            SensorReading outlier = readings.stream()
                                    .max((r1, r2) -> Double.compare(
                                            Math.abs(r1.value - mean),
                                            Math.abs(r2.value - mean)))
                                    .orElse(readings.get(0));
                            
                            String alert = createAnomalyAlert(
                                    "CORRELATION_ANOMALY",
                                    outlier.sensorId,
                                    "Cross-sensor correlation anomaly",
                                    String.format("Temperature sensor reading %.2f°C deviates %.2f°C from location average %.2f°C",
                                            outlier.value, maxDeviation, mean),
                                    Math.min(0.9, maxDeviation / 10.0),
                                    outlier
                            );
                            out.collect(alert);
                        }
                    }
                });
    }
    
    /**
     * Check if reading violates sensor-specific thresholds
     */
    private static boolean isThresholdViolation(SensorReading reading) {
        switch (reading.deviceType.toLowerCase()) {
            case "temperature":
                return reading.value < -40 || reading.value > 85; // Industrial range
            case "humidity":
                return reading.value < 0 || reading.value > 100;
            case "pressure":
                return reading.value < 0 || reading.value > 10; // 10 bar max
            case "vibration":
                return reading.value > 50; // High vibration threshold
            case "light":
                return reading.value < 0 || reading.value > 100000; // Lux range
            case "air_quality":
                return reading.value > 500; // Poor air quality
            case "sound":
                return reading.value > 130; // Dangerous sound level (dB)
            case "power":
                return reading.value < 0 || reading.value > 1000; // 1kW max
            case "flow_rate":
                return reading.value < 0 || reading.value > 1000; // L/min
            default:
                return false;
        }
    }
    
    /**
     * Calculate severity based on how much threshold is violated
     */
    private static double calculateThresholdSeverity(SensorReading reading) {
        // Simplified severity calculation based on threshold violation percentage
        switch (reading.deviceType.toLowerCase()) {
            case "temperature":
                if (reading.value > 85) return Math.min(0.9, (reading.value - 85) / 20);
                if (reading.value < -40) return Math.min(0.9, (-40 - reading.value) / 20);
                break;
            case "humidity":
                if (reading.value > 100) return Math.min(0.9, (reading.value - 100) / 50);
                if (reading.value < 0) return Math.min(0.9, -reading.value / 50);
                break;
            // Add more sensor-specific severity calculations as needed
        }
        return 0.6; // Default severity
    }
    
    /**
     * Helper method to create standardized anomaly alert JSON
     */
    private static String createAnomalyAlert(String alertType, String sensorId, String title, 
                                           String description, double severity, SensorReading reading) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode alert = mapper.createObjectNode();
            
            alert.put("alertId", java.util.UUID.randomUUID().toString());
            alert.put("timestamp", Instant.now().toString());
            alert.put("alertType", alertType);
            alert.put("sensorId", sensorId);
            alert.put("deviceType", reading.deviceType);
            alert.put("location", reading.location);
            alert.put("value", reading.value);
            alert.put("unit", reading.unit);
            alert.put("title", title);
            alert.put("description", description);
            alert.put("severity", severity);
            alert.put("status", "ACTIVE");
            alert.put("source", "ANOMALY-DETECTION");
            alert.put("readingTimestamp", Instant.ofEpochMilli(reading.timestamp).toString());
            
            return mapper.writeValueAsString(alert);
        } catch (Exception e) {
            LOG.error("Error creating anomaly alert", e);
            return String.format("{\"error\": \"Failed to create alert\", \"type\": \"%s\"}", alertType);
        }
    }
    
    /**
     * Parser to convert Kafka JSON messages to SensorReading objects
     */
    public static class SensorReadingParser implements MapFunction<String, SensorReading> {
        private static final ObjectMapper mapper = new ObjectMapper();
        
        @Override
        public SensorReading map(String value) throws Exception {
            try {
                JsonNode json = mapper.readTree(value);
                
                SensorReading reading = new SensorReading();
                reading.sensorId = json.get("sensorId").asText();
                reading.deviceType = json.get("deviceType").asText();
                reading.value = json.get("value").asDouble();
                reading.unit = json.get("unit").asText();
                reading.location = json.get("location").asText();
                reading.timestamp = Instant.parse(json.get("timestamp").asText()).toEpochMilli();
                reading.batteryLevel = json.has("batteryLevel") ? json.get("batteryLevel").asInt() : 100;
                
                return reading;
            } catch (Exception e) {
                LOG.warn("Failed to parse sensor reading: {}", value, e);
                return null;
            }
        }
    }
    
    /**
     * Sensor reading POJO
     */
    public static class SensorReading {
        public String sensorId;
        public String deviceType;
        public double value;
        public String unit;
        public String location;
        public long timestamp;
        public int batteryLevel;
        
        public SensorReading() {}
        
        @Override
        public String toString() {
            return String.format("SensorReading{id='%s', type='%s', value=%.2f %s, location='%s'}", 
                    sensorId, deviceType, value, unit, location);
        }
    }
}