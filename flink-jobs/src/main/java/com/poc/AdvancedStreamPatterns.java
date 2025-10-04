package com.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.elasticsearch.sink.Elasticsearch7SinkBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class AdvancedStreamPatterns {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(30000);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);
        
        // Configure parallelism
        env.setParallelism(4);
        
        // Kafka sources for different event types
        KafkaSource<String> userEventSource = createKafkaSource("user-events");
        KafkaSource<String> transactionEventSource = createKafkaSource("transaction-events");
        KafkaSource<String> iotSensorEventSource = createKafkaSource("iot-sensor-events");
        
        // Create data streams
        DataStream<JsonNode> userEvents = env.fromSource(userEventSource, 
            WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((event, timestamp) -> extractTimestamp(event)),
            "user-events-source")
            .map(AdvancedStreamPatterns::parseJson);
            
        DataStream<JsonNode> transactionEvents = env.fromSource(transactionEventSource,
            WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((event, timestamp) -> extractTimestamp(event)),
            "transaction-events-source")
            .map(AdvancedStreamPatterns::parseJson);
            
        DataStream<JsonNode> iotSensorEvents = env.fromSource(iotSensorEventSource,
            WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((event, timestamp) -> extractTimestamp(event)),
            "iot-sensor-events-source")
            .map(AdvancedStreamPatterns::parseJson);
        
        // Pattern 1: Stream Enrichment - Enrich transactions with user profile data
        DataStream<JsonNode> enrichedTransactions = implementStreamEnrichment(userEvents, transactionEvents);
        
        // Pattern 2: Temporal Pattern Detection - Detect rapid consecutive transactions
        DataStream<JsonNode> rapidTransactionAlerts = implementTemporalPatternDetection(transactionEvents);
        
        // Pattern 3: Multi-Stream Join - Join IoT sensor data with transaction data based on location
        DataStream<JsonNode> locationCorrelatedEvents = implementMultiStreamJoin(transactionEvents, iotSensorEvents);
        
        // Pattern 4: Complex Windowed Aggregations - Multiple window types on IoT data
        DataStream<JsonNode> iotAggregations = implementComplexWindowing(iotSensorEvents);
        
        // Pattern 5: Side Output Pattern - Separate normal and anomalous events
        SingleOutputStreamOperator<JsonNode> processedEvents = implementSideOutputPattern(transactionEvents);
        OutputTag<JsonNode> anomalyOutputTag = new OutputTag<JsonNode>("anomalies") {};
        DataStream<JsonNode> anomalies = processedEvents.getSideOutput(anomalyOutputTag);
        
        // Pattern 6: Dynamic Schema Evolution - Handle schema changes gracefully
        DataStream<JsonNode> evolvedSchemaEvents = implementSchemaEvolution(userEvents);
        
        // Pattern 7: Backpressure Handling - Rate limiting and flow control
        DataStream<JsonNode> rateLimitedEvents = implementBackpressureHandling(transactionEvents);
        
        // Output all patterns to Elasticsearch with different indices
        outputToElasticsearch(enrichedTransactions, "enriched-transactions");
        outputToElasticsearch(rapidTransactionAlerts, "rapid-transaction-alerts");
        outputToElasticsearch(locationCorrelatedEvents, "location-correlated-events");
        outputToElasticsearch(iotAggregations, "iot-aggregations");
        outputToElasticsearch(processedEvents, "processed-events");
        outputToElasticsearch(anomalies, "anomaly-events");
        outputToElasticsearch(evolvedSchemaEvents, "evolved-schema-events");
        outputToElasticsearch(rateLimitedEvents, "rate-limited-events");
        
        env.execute("Advanced Stream Processing Patterns");
    }
    
    // Pattern 1: Stream Enrichment
    private static DataStream<JsonNode> implementStreamEnrichment(
            DataStream<JsonNode> userEvents, 
            DataStream<JsonNode> transactionEvents) {
        
        return transactionEvents
            .keyBy(event -> event.get("userId").asText())
            .connect(userEvents.keyBy(event -> event.get("userId").asText()))
            .process(new StreamEnrichmentFunction());
    }
    
    // Pattern 2: Temporal Pattern Detection
    private static DataStream<JsonNode> implementTemporalPatternDetection(DataStream<JsonNode> transactionEvents) {
        return transactionEvents
            .keyBy(event -> event.get("userId").asText())
            .process(new RapidTransactionDetector());
    }
    
    // Pattern 3: Multi-Stream Join
    private static DataStream<JsonNode> implementMultiStreamJoin(
            DataStream<JsonNode> transactionEvents,
            DataStream<JsonNode> iotSensorEvents) {
        
        return transactionEvents
            .keyBy(event -> extractLocation(event))
            .connect(iotSensorEvents.keyBy(event -> event.get("location").asText()))
            .process(new LocationBasedJoinFunction());
    }
    
    // Pattern 4: Complex Windowed Aggregations
    private static DataStream<JsonNode> implementComplexWindowing(DataStream<JsonNode> iotSensorEvents) {
        
        // Tumbling window aggregations
        DataStream<JsonNode> tumblingAggregations = iotSensorEvents
            .keyBy(event -> event.get("sensorType").asText())
            .window(TumblingEventTimeWindows.of(Time.minutes(5)))
            .aggregate(new IoTAggregateFunction(), new IoTWindowProcessFunction());
        
        // Sliding window aggregations for trend detection
        DataStream<JsonNode> slidingAggregations = iotSensorEvents
            .keyBy(event -> event.get("sensorType").asText())
            .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.minutes(2)))
            .aggregate(new IoTTrendAggregateFunction(), new IoTTrendWindowProcessFunction());
        
        return tumblingAggregations.union(slidingAggregations);
    }
    
    // Pattern 5: Side Output Pattern
    private static SingleOutputStreamOperator<JsonNode> implementSideOutputPattern(DataStream<JsonNode> transactionEvents) {
        OutputTag<JsonNode> anomalyOutputTag = new OutputTag<JsonNode>("anomalies") {};
        
        return transactionEvents.process(new ProcessFunction(anomalyOutputTag));
    }
    
    // Pattern 6: Schema Evolution
    private static DataStream<JsonNode> implementSchemaEvolution(DataStream<JsonNode> userEvents) {
        return userEvents.map(new SchemaEvolutionFunction());
    }
    
    // Pattern 7: Backpressure Handling
    private static DataStream<JsonNode> implementBackpressureHandling(DataStream<JsonNode> transactionEvents) {
        return transactionEvents
            .keyBy(event -> event.get("userId").asText())
            .process(new RateLimitingFunction(100)); // 100 events per minute per user
    }
    
    // Helper Classes and Functions
    
    private static class StreamEnrichmentFunction extends CoProcessFunction<JsonNode, JsonNode, JsonNode> {
        private ValueState<JsonNode> userProfileState;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            userProfileState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("userProfile", JsonNode.class));
        }
        
        @Override
        public void processElement1(JsonNode transaction, Context ctx, Collector<JsonNode> out) throws Exception {
            JsonNode userProfile = userProfileState.value();
            if (userProfile != null) {
                ObjectNode enriched = (ObjectNode) transaction.deepCopy();
                enriched.set("userProfile", userProfile);
                enriched.put("enrichmentTimestamp", Instant.now().toEpochMilli());
                out.collect(enriched);
            }
        }
        
        @Override
        public void processElement2(JsonNode userEvent, Context ctx, Collector<JsonNode> out) throws Exception {
            userProfileState.update(userEvent);
        }
    }
    
    private static class RapidTransactionDetector extends KeyedProcessFunction<String, JsonNode, JsonNode> {
        private ValueState<List<Long>> recentTransactionTimes;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            recentTransactionTimes = getRuntimeContext().getState(
                new ValueStateDescriptor<>("recentTransactionTimes", Types.LIST(Types.LONG)));
        }
        
        @Override
        public void processElement(JsonNode transaction, Context ctx, Collector<JsonNode> out) throws Exception {
            List<Long> times = recentTransactionTimes.value();
            if (times == null) {
                times = new ArrayList<>();
            }
            
            long currentTime = ctx.timestamp();
            times.add(currentTime);
            
            // Keep only transactions from last 5 minutes
            times.removeIf(time -> currentTime - time > 300000);
            
            // Alert if more than 5 transactions in 5 minutes
            if (times.size() > 5) {
                ObjectNode alert = objectMapper.createObjectNode();
                alert.put("alertType", "RAPID_TRANSACTIONS");
                alert.put("userId", transaction.get("userId").asText());
                alert.put("transactionCount", times.size());
                alert.put("timeWindow", "5_minutes");
                alert.put("timestamp", currentTime);
                alert.set("originalTransaction", transaction);
                out.collect(alert);
            }
            
            recentTransactionTimes.update(times);
        }
    }
    
    private static class LocationBasedJoinFunction extends CoProcessFunction<JsonNode, JsonNode, JsonNode> {
        private ValueState<JsonNode> latestSensorData;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            latestSensorData = getRuntimeContext().getState(
                new ValueStateDescriptor<>("latestSensorData", JsonNode.class));
        }
        
        @Override
        public void processElement1(JsonNode transaction, Context ctx, Collector<JsonNode> out) throws Exception {
            JsonNode sensorData = latestSensorData.value();
            if (sensorData != null) {
                ObjectNode joined = (ObjectNode) transaction.deepCopy();
                joined.set("locationSensorData", sensorData);
                joined.put("joinTimestamp", ctx.timestamp());
                out.collect(joined);
            }
        }
        
        @Override
        public void processElement2(JsonNode sensorEvent, Context ctx, Collector<JsonNode> out) throws Exception {
            latestSensorData.update(sensorEvent);
        }
    }
    
    private static class IoTAggregateFunction implements AggregateFunction<JsonNode, Tuple3<Double, Double, Integer>, Tuple3<Double, Double, Integer>> {
        @Override
        public Tuple3<Double, Double, Integer> createAccumulator() {
            return new Tuple3<>(0.0, Double.MAX_VALUE, 0);
        }
        
        @Override
        public Tuple3<Double, Double, Integer> add(JsonNode value, Tuple3<Double, Double, Integer> accumulator) {
            double sensorValue = value.get("value").asDouble();
            return new Tuple3<>(
                accumulator.f0 + sensorValue, // sum
                Math.min(accumulator.f1, sensorValue), // min
                accumulator.f2 + 1 // count
            );
        }
        
        @Override
        public Tuple3<Double, Double, Integer> getResult(Tuple3<Double, Double, Integer> accumulator) {
            return accumulator;
        }
        
        @Override
        public Tuple3<Double, Double, Integer> merge(Tuple3<Double, Double, Integer> a, Tuple3<Double, Double, Integer> b) {
            return new Tuple3<>(
                a.f0 + b.f0,
                Math.min(a.f1, b.f1),
                a.f2 + b.f2
            );
        }
    }
    
    private static class IoTWindowProcessFunction extends ProcessWindowFunction<Tuple3<Double, Double, Integer>, JsonNode, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<Tuple3<Double, Double, Integer>> elements, Collector<JsonNode> out) throws Exception {
            Tuple3<Double, Double, Integer> result = elements.iterator().next();
            
            ObjectNode aggregation = objectMapper.createObjectNode();
            aggregation.put("sensorType", key);
            aggregation.put("windowStart", context.window().getStart());
            aggregation.put("windowEnd", context.window().getEnd());
            aggregation.put("count", result.f2);
            aggregation.put("sum", result.f0);
            aggregation.put("average", result.f0 / result.f2);
            aggregation.put("minimum", result.f1);
            aggregation.put("aggregationType", "tumbling_5min");
            
            out.collect(aggregation);
        }
    }
    
    private static class IoTTrendAggregateFunction implements AggregateFunction<JsonNode, List<Double>, List<Double>> {
        @Override
        public List<Double> createAccumulator() {
            return new ArrayList<>();
        }
        
        @Override
        public List<Double> add(JsonNode value, List<Double> accumulator) {
            accumulator.add(value.get("value").asDouble());
            return accumulator;
        }
        
        @Override
        public List<Double> getResult(List<Double> accumulator) {
            return accumulator;
        }
        
        @Override
        public List<Double> merge(List<Double> a, List<Double> b) {
            List<Double> merged = new ArrayList<>(a);
            merged.addAll(b);
            return merged;
        }
    }
    
    private static class IoTTrendWindowProcessFunction extends ProcessWindowFunction<List<Double>, JsonNode, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<List<Double>> elements, Collector<JsonNode> out) throws Exception {
            List<Double> values = elements.iterator().next();
            
            if (values.size() < 2) return;
            
            // Calculate trend (simple linear regression slope)
            double trend = calculateTrend(values);
            
            ObjectNode trendAnalysis = objectMapper.createObjectNode();
            trendAnalysis.put("sensorType", key);
            trendAnalysis.put("windowStart", context.window().getStart());
            trendAnalysis.put("windowEnd", context.window().getEnd());
            trendAnalysis.put("dataPoints", values.size());
            trendAnalysis.put("trend", trend);
            trendAnalysis.put("trendDirection", trend > 0 ? "INCREASING" : trend < 0 ? "DECREASING" : "STABLE");
            trendAnalysis.put("aggregationType", "sliding_10min_2min");
            
            out.collect(trendAnalysis);
        }
        
        private double calculateTrend(List<Double> values) {
            int n = values.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            
            for (int i = 0; i < n; i++) {
                sumX += i;
                sumY += values.get(i);
                sumXY += i * values.get(i);
                sumX2 += i * i;
            }
            
            return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        }
    }
    
    private static class ProcessFunction extends KeyedProcessFunction<String, JsonNode, JsonNode> {
        private final OutputTag<JsonNode> anomalyOutputTag;
        
        public ProcessFunction(OutputTag<JsonNode> anomalyOutputTag) {
            this.anomalyOutputTag = anomalyOutputTag;
        }
        
        @Override
        public void processElement(JsonNode transaction, Context ctx, Collector<JsonNode> out) throws Exception {
            double amount = transaction.get("amount").asDouble();
            
            // Simple anomaly detection based on amount
            if (amount > 10000) {
                ObjectNode anomaly = (ObjectNode) transaction.deepCopy();
                anomaly.put("anomalyType", "HIGH_AMOUNT");
                anomaly.put("detectionTimestamp", ctx.timestamp());
                ctx.output(anomalyOutputTag, anomaly);
            } else {
                out.collect(transaction);
            }
        }
    }
    
    private static class SchemaEvolutionFunction extends RichMapFunction<JsonNode, JsonNode> {
        @Override
        public JsonNode map(JsonNode event) throws Exception {
            ObjectNode evolved = (ObjectNode) event.deepCopy();
            
            // Add schema version
            evolved.put("schemaVersion", "2.0");
            
            // Migrate old field names
            if (event.has("user_id") && !event.has("userId")) {
                evolved.put("userId", event.get("user_id").asText());
                evolved.remove("user_id");
            }
            
            // Add default values for missing fields
            if (!evolved.has("region")) {
                evolved.put("region", "unknown");
            }
            
            // Normalize data types
            if (evolved.has("timestamp") && evolved.get("timestamp").isTextual()) {
                try {
                    long timestamp = Long.parseLong(evolved.get("timestamp").asText());
                    evolved.put("timestamp", timestamp);
                } catch (NumberFormatException e) {
                    evolved.put("timestamp", System.currentTimeMillis());
                }
            }
            
            return evolved;
        }
    }
    
    private static class RateLimitingFunction extends KeyedProcessFunction<String, JsonNode, JsonNode> {
        private final int maxEventsPerMinute;
        private ValueState<Tuple2<Integer, Long>> rateLimitState;
        
        public RateLimitingFunction(int maxEventsPerMinute) {
            this.maxEventsPerMinute = maxEventsPerMinute;
        }
        
        @Override
        public void open(Configuration parameters) throws Exception {
            rateLimitState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("rateLimitState", Types.TUPLE(Types.INT, Types.LONG)));
        }
        
        @Override
        public void processElement(JsonNode event, Context ctx, Collector<JsonNode> out) throws Exception {
            Tuple2<Integer, Long> currentState = rateLimitState.value();
            long currentTime = ctx.timestamp();
            long currentMinute = currentTime / 60000;
            
            if (currentState == null || currentState.f1 != currentMinute) {
                // New minute, reset counter
                currentState = new Tuple2<>(1, currentMinute);
            } else {
                currentState.f0++;
            }
            
            if (currentState.f0 <= maxEventsPerMinute) {
                out.collect(event);
            }
            // Events exceeding rate limit are dropped (backpressure)
            
            rateLimitState.update(currentState);
        }
    }
    
    // Utility Methods
    
    private static KafkaSource<String> createKafkaSource(String topic) {
        return KafkaSource.<String>builder()
            .setBootstrapServers("kafka1:29092,kafka2:29093,kafka3:29094")
            .setTopics(topic)
            .setGroupId("advanced-patterns-group")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
    }
    
    private static JsonNode parseJson(String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", "Invalid JSON");
            errorNode.put("original", jsonString);
            return errorNode;
        }
    }
    
    private static long extractTimestamp(String event) {
        try {
            JsonNode node = objectMapper.readTree(event);
            return node.get("timestamp").asLong();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
    
    private static String extractLocation(JsonNode event) {
        return event.has("location") ? event.get("location").asText() : "unknown";
    }
    
    private static void outputToElasticsearch(DataStream<JsonNode> stream, String index) {
        stream.sinkTo(
            new Elasticsearch7SinkBuilder<JsonNode>()
                .setBulkFlushMaxActions(1000)
                .setBulkFlushMaxSizeMb(5)
                .setBulkFlushInterval(5000)
                .setHosts(new HttpHost("elasticsearch", 9200, "http"))
                .setEmitter((element, context, indexer) -> {
                    IndexRequest indexRequest = Requests.indexRequest()
                        .index(index)
                        .id(UUID.randomUUID().toString())
                        .source(element.toString(), org.elasticsearch.xcontent.XContentType.JSON);
                    indexer.add(indexRequest);
                })
                .build()
        );
    }
}