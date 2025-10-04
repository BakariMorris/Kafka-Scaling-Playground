package com.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.elasticsearch.sink.Elasticsearch7SinkBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.Meter;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.Collector;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PerformanceOptimizedStreamJob {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Performance optimizations
        configurePerformanceOptimizations(env);
        
        // Create optimized Kafka sources
        KafkaSource<String> userEventSource = createOptimizedKafkaSource("user-events");
        KafkaSource<String> transactionEventSource = createOptimizedKafkaSource("transaction-events");
        KafkaSource<String> iotSensorEventSource = createOptimizedKafkaSource("iot-sensor-events");
        
        // Create high-performance data streams
        DataStream<JsonNode> userEvents = createOptimizedStream(env, userEventSource, "user-events-source");
        DataStream<JsonNode> transactionEvents = createOptimizedStream(env, transactionEventSource, "transaction-events-source");
        DataStream<JsonNode> iotSensorEvents = createOptimizedStream(env, iotSensorEventSource, "iot-sensor-events-source");
        
        // Apply performance optimizations
        DataStream<JsonNode> optimizedUserEvents = userEvents
            .map(new PerformanceMetricsMapper("user_events"))
            .keyBy(event -> event.get("userId").asText())
            .process(new PerformanceOptimizedProcessor())
            .name("Optimized User Event Processing");
        
        DataStream<JsonNode> optimizedTransactionEvents = transactionEvents
            .map(new PerformanceMetricsMapper("transaction_events"))
            .keyBy(event -> event.get("userId").asText())
            .process(new CachingPerformanceProcessor())
            .name("Optimized Transaction Processing");
        
        DataStream<JsonNode> optimizedIoTEvents = iotSensorEvents
            .map(new PerformanceMetricsMapper("iot_events"))
            .keyBy(event -> event.get("sensorType").asText())
            .process(new BatchingPerformanceProcessor())
            .name("Optimized IoT Processing");
        
        // High-performance sinks
        createOptimizedElasticsearchSink(optimizedUserEvents, "optimized-user-events");
        createOptimizedElasticsearchSink(optimizedTransactionEvents, "optimized-transaction-events");
        createOptimizedElasticsearchSink(optimizedIoTEvents, "optimized-iot-events");
        
        env.execute("Performance Optimized Stream Processing Job");
    }
    
    private static void configurePerformanceOptimizations(StreamExecutionEnvironment env) {
        // Enable checkpointing with optimized settings
        env.enableCheckpointing(60000); // 1 minute
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000); // 30 seconds
        env.getCheckpointConfig().setCheckpointTimeout(300000); // 5 minutes
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        
        // Configure parallelism for optimal resource utilization
        env.setParallelism(8); // Adjust based on available cores
        
        // Enable object reuse for better memory efficiency
        env.getConfig().enableObjectReuse();
        
        // Configure buffer timeout for better throughput
        env.setBufferTimeout(100); // 100ms
        
        // Configure restart strategy
        env.getConfig().setRestartStrategy(
            org.apache.flink.api.common.restarting.RestartStrategies.fixedDelayRestart(
                3, // number of restart attempts
                org.apache.flink.api.common.time.Time.of(30, java.util.concurrent.TimeUnit.SECONDS) // delay
            )
        );
    }
    
    private static KafkaSource<String> createOptimizedKafkaSource(String topic) {
        return KafkaSource.<String>builder()
            .setBootstrapServers("kafka1:29092,kafka2:29093,kafka3:29094")
            .setTopics(topic)
            .setGroupId("performance-optimized-group")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            // Performance optimizations
            .setProperty("fetch.min.bytes", "65536") // 64KB
            .setProperty("fetch.max.wait.ms", "500")
            .setProperty("max.poll.records", "1000")
            .setProperty("receive.buffer.bytes", "262144") // 256KB
            .setProperty("send.buffer.bytes", "262144") // 256KB
            .build();
    }
    
    private static DataStream<JsonNode> createOptimizedStream(
            StreamExecutionEnvironment env, 
            KafkaSource<String> source, 
            String sourceName) {
        
        return env.fromSource(source,
            WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                .withTimestampAssigner((event, timestamp) -> extractTimestamp(event))
                .withIdleness(Duration.ofMinutes(1)), // Handle idle partitions
            sourceName)
            .map(PerformanceOptimizedStreamJob::parseJsonOptimized)
            .filter(Objects::nonNull) // Remove invalid JSON
            .name("Optimized JSON Parsing");
    }
    
    // Performance-optimized JSON parsing with caching
    private static JsonNode parseJsonOptimized(String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            // Return null for invalid JSON to be filtered out
            return null;
        }
    }
    
    private static long extractTimestamp(String event) {
        try {
            JsonNode node = objectMapper.readTree(event);
            return node.has("timestamp") ? node.get("timestamp").asLong() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
    
    // Performance metrics mapper
    public static class PerformanceMetricsMapper extends RichMapFunction<JsonNode, JsonNode> {
        private final String eventType;
        private transient Counter processedEvents;
        private transient Meter eventsPerSecond;
        private transient Histogram processingLatency;
        
        public PerformanceMetricsMapper(String eventType) {
            this.eventType = eventType;
        }
        
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            // Register custom metrics
            processedEvents = getRuntimeContext()
                .getMetricGroup()
                .addGroup("performance", eventType)
                .counter("processed_events");
            
            eventsPerSecond = getRuntimeContext()
                .getMetricGroup()
                .addGroup("performance", eventType)
                .meter("events_per_second", new org.apache.flink.dropwizard.metrics.DropwizardMeterWrapper(
                    new com.codahale.metrics.Meter()));
            
            processingLatency = getRuntimeContext()
                .getMetricGroup()
                .addGroup("performance", eventType)
                .histogram("processing_latency", new org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper(
                    new com.codahale.metrics.Histogram(new com.codahale.metrics.ExponentiallyDecayingReservoir())));
        }
        
        @Override
        public JsonNode map(JsonNode event) throws Exception {
            long startTime = System.nanoTime();
            
            // Add processing metadata
            ObjectNode enriched = (ObjectNode) event.deepCopy();
            enriched.put("processingStartTime", System.currentTimeMillis());
            enriched.put("processorId", getRuntimeContext().getTaskNameWithSubtasks());
            
            // Update metrics
            processedEvents.inc();
            eventsPerSecond.markEvent();
            
            long endTime = System.nanoTime();
            processingLatency.update((endTime - startTime) / 1_000_000); // Convert to milliseconds
            
            return enriched;
        }
    }
    
    // High-performance processor with state optimization
    public static class PerformanceOptimizedProcessor extends KeyedProcessFunction<String, JsonNode, JsonNode> {
        private transient ValueState<Long> lastProcessingTime;
        private transient MapState<String, Integer> eventCounts;
        private transient Counter stateAccesses;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            // Optimized state descriptors with TTL
            ValueStateDescriptor<Long> lastTimeDescriptor = new ValueStateDescriptor<>(
                "lastProcessingTime",
                TypeInformation.of(Long.class)
            );
            lastTimeDescriptor.enableTimeToLive(
                org.apache.flink.api.common.state.StateTtlConfig.newBuilder(org.apache.flink.api.common.time.Time.hours(24))
                    .setUpdateType(org.apache.flink.api.common.state.StateTtlConfig.UpdateType.OnCreateAndWrite)
                    .setStateVisibility(org.apache.flink.api.common.state.StateTtlConfig.StateVisibility.NeverReturnExpired)
                    .build()
            );
            lastProcessingTime = getRuntimeContext().getState(lastTimeDescriptor);
            
            MapStateDescriptor<String, Integer> countsDescriptor = new MapStateDescriptor<>(
                "eventCounts",
                String.class,
                Integer.class
            );
            countsDescriptor.enableTimeToLive(
                org.apache.flink.api.common.state.StateTtlConfig.newBuilder(org.apache.flink.api.common.time.Time.hours(1))
                    .setUpdateType(org.apache.flink.api.common.state.StateTtlConfig.UpdateType.OnCreateAndWrite)
                    .setStateVisibility(org.apache.flink.api.common.state.StateTtlConfig.StateVisibility.NeverReturnExpired)
                    .build()
            );
            eventCounts = getRuntimeContext().getMapState(countsDescriptor);
            
            stateAccesses = getRuntimeContext()
                .getMetricGroup()
                .addGroup("performance")
                .counter("state_accesses");
        }
        
        @Override
        public void processElement(JsonNode event, Context ctx, Collector<JsonNode> out) throws Exception {
            long currentTime = ctx.timestamp();
            String eventType = event.has("action") ? event.get("action").asText() : "unknown";
            
            // Optimized state access
            Long lastTime = lastProcessingTime.value();
            stateAccesses.inc();
            
            // Update event counts efficiently
            Integer currentCount = eventCounts.get(eventType);
            eventCounts.put(eventType, currentCount == null ? 1 : currentCount + 1);
            stateAccesses.inc();
            
            // Add performance metadata
            ObjectNode optimized = (ObjectNode) event.deepCopy();
            optimized.put("lastProcessingInterval", lastTime != null ? currentTime - lastTime : 0);
            optimized.put("eventTypeCount", eventCounts.get(eventType));
            optimized.put("optimizationApplied", true);
            
            lastProcessingTime.update(currentTime);
            stateAccesses.inc();
            
            out.collect(optimized);
        }
    }
    
    // Caching processor for frequent lookups
    public static class CachingPerformanceProcessor extends KeyedProcessFunction<String, JsonNode, JsonNode> {
        private transient Map<String, Object> localCache;
        private transient Counter cacheHits;
        private transient Counter cacheMisses;
        private static final int CACHE_SIZE = 1000;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            // Initialize LRU cache
            localCache = new LinkedHashMap<String, Object>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                    return size() > CACHE_SIZE;
                }
            };
            
            cacheHits = getRuntimeContext()
                .getMetricGroup()
                .addGroup("cache")
                .counter("hits");
            
            cacheMisses = getRuntimeContext()
                .getMetricGroup()
                .addGroup("cache")
                .counter("misses");
        }
        
        @Override
        public void processElement(JsonNode event, Context ctx, Collector<JsonNode> out) throws Exception {
            String userId = event.get("userId").asText();
            String cacheKey = "user_profile_" + userId;
            
            // Check cache first
            Object cachedProfile = localCache.get(cacheKey);
            if (cachedProfile != null) {
                cacheHits.inc();
            } else {
                cacheMisses.inc();
                // Simulate profile lookup and cache it
                cachedProfile = createUserProfile(userId);
                localCache.put(cacheKey, cachedProfile);
            }
            
            // Enrich event with cached data
            ObjectNode enriched = (ObjectNode) event.deepCopy();
            enriched.put("cacheHit", cachedProfile != null);
            enriched.put("cacheSize", localCache.size());
            
            out.collect(enriched);
        }
        
        private Object createUserProfile(String userId) {
            // Simulate expensive lookup operation
            return Map.of(
                "userId", userId,
                "tier", "premium",
                "riskScore", ThreadLocalRandom.current().nextDouble(0.0, 1.0)
            );
        }
    }
    
    // Batching processor for high-throughput scenarios
    public static class BatchingPerformanceProcessor extends KeyedProcessFunction<String, JsonNode, JsonNode> {
        private transient List<JsonNode> batch;
        private transient long lastBatchTime;
        private transient Counter batchesProcessed;
        private static final int BATCH_SIZE = 100;
        private static final long BATCH_TIMEOUT_MS = 5000; // 5 seconds
        
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            batch = new ArrayList<>(BATCH_SIZE);
            lastBatchTime = System.currentTimeMillis();
            
            batchesProcessed = getRuntimeContext()
                .getMetricGroup()
                .addGroup("batching")
                .counter("batches_processed");
        }
        
        @Override
        public void processElement(JsonNode event, Context ctx, Collector<JsonNode> out) throws Exception {
            batch.add(event);
            
            long currentTime = System.currentTimeMillis();
            boolean shouldFlush = batch.size() >= BATCH_SIZE || 
                                 (currentTime - lastBatchTime) >= BATCH_TIMEOUT_MS;
            
            if (shouldFlush) {
                processBatch(out);
                batch.clear();
                lastBatchTime = currentTime;
                batchesProcessed.inc();
            }
        }
        
        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<JsonNode> out) throws Exception {
            if (!batch.isEmpty()) {
                processBatch(out);
                batch.clear();
                batchesProcessed.inc();
            }
        }
        
        private void processBatch(Collector<JsonNode> out) {
            // Process entire batch efficiently
            double avgValue = batch.stream()
                .filter(event -> event.has("value"))
                .mapToDouble(event -> event.get("value").asDouble())
                .average()
                .orElse(0.0);
            
            // Emit batch summary
            ObjectNode batchSummary = objectMapper.createObjectNode();
            batchSummary.put("batchSize", batch.size());
            batchSummary.put("averageValue", avgValue);
            batchSummary.put("processingTime", System.currentTimeMillis());
            batchSummary.put("batchId", UUID.randomUUID().toString());
            
            out.collect(batchSummary);
            
            // Emit individual events with batch context
            for (JsonNode event : batch) {
                ObjectNode enriched = (ObjectNode) event.deepCopy();
                enriched.put("batchProcessed", true);
                enriched.put("batchSize", batch.size());
                enriched.put("batchAverage", avgValue);
                out.collect(enriched);
            }
        }
    }
    
    // High-performance Elasticsearch sink
    private static void createOptimizedElasticsearchSink(DataStream<JsonNode> stream, String index) {
        stream.sinkTo(
            new Elasticsearch7SinkBuilder<JsonNode>()
                // Optimized bulk settings
                .setBulkFlushMaxActions(5000) // Increased batch size
                .setBulkFlushMaxSizeMb(20) // Increased batch size in MB
                .setBulkFlushInterval(2000) // Reduced flush interval for better throughput
                .setHosts(new HttpHost("elasticsearch", 9200, "http"))
                .setEmitter((element, context, indexer) -> {
                    String documentId = element.has("id") ? 
                        element.get("id").asText() : 
                        UUID.randomUUID().toString();
                    
                    IndexRequest indexRequest = Requests.indexRequest()
                        .index(index)
                        .id(documentId)
                        .source(element.toString(), org.elasticsearch.xcontent.XContentType.JSON);
                    
                    indexer.add(indexRequest);
                })
                .build()
        ).name("Optimized Elasticsearch Sink: " + index);
    }
}