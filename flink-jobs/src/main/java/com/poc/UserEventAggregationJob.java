package com.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Flink job for aggregating user events from Kafka
 * Demonstrates tumbling windows, keyed streams, and basic aggregations
 */
public class UserEventAggregationJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(UserEventAggregationJob.class);
    
    // Configuration constants
    private static final String KAFKA_BROKERS = "kafka1:29092,kafka2:29093,kafka3:29094";
    private static final String INPUT_TOPIC = "user-events";
    private static final String OUTPUT_TOPIC = "aggregated-events";
    private static final String JOB_NAME = "UserEventAggregationJob";
    
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
        
        LOG.info("Starting User Event Aggregation Job");
        
        // Create Kafka source
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setTopics(INPUT_TOPIC)
                .setGroupId("user-event-aggregation-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        
        // Create data stream from Kafka source
        DataStream<String> kafkaStream = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");
        
        // Parse JSON events and extract timestamps
        SingleOutputStreamOperator<UserEvent> parsedEvents = kafkaStream
                .map(new JsonParserFunction())
                .name("Parse JSON Events");
        
        // Assign timestamps and watermarks for event time processing
        SingleOutputStreamOperator<UserEvent> timestampedEvents = parsedEvents
                .assignTimestampsAndWatermarks(
                    WatermarkStrategy.<UserEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                            .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                )
                .name("Assign Timestamps");
        
        // 1. Aggregate events by user and event type in 1-minute tumbling windows
        SingleOutputStreamOperator<EventAggregation> userEventTypeAggregation = timestampedEvents
                .keyBy(event -> event.getUserId() + ":" + event.getEventType())
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .apply(new UserEventTypeAggregationFunction())
                .name("User Event Type Aggregation");
        
        // 2. Aggregate events by page in 5-minute tumbling windows
        SingleOutputStreamOperator<PageAggregation> pageAggregation = timestampedEvents
                .filter(event -> event.getPage() != null)
                .keyBy(UserEvent::getPage)
                .window(TumblingEventTimeWindows.of(Time.minutes(5)))
                .apply(new PageAggregationFunction())
                .name("Page Aggregation");
        
        // 3. Session analysis - track user sessions
        SingleOutputStreamOperator<SessionInfo> sessionAnalysis = timestampedEvents
                .keyBy(UserEvent::getUserId)
                .process(new SessionAnalysisFunction())
                .name("Session Analysis");
        
        // 4. Real-time metrics - events per second
        SingleOutputStreamOperator<MetricsInfo> realTimeMetrics = timestampedEvents
                .keyBy(event -> "global")
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .apply(new RealTimeMetricsFunction())
                .name("Real-time Metrics");
        
        // Print results to console for debugging
        userEventTypeAggregation.print("User-EventType Aggregation");
        pageAggregation.print("Page Aggregation");
        sessionAnalysis.print("Session Analysis");
        realTimeMetrics.print("Real-time Metrics");
        
        // Execute the job
        env.execute(JOB_NAME);
    }
    
    // Data classes for representing events and aggregations
    public static class UserEvent {
        private String userId;
        private String sessionId;
        private String eventType;
        private String page;
        private String country;
        private long timestamp;
        private Map<String, Object> additionalData;
        
        public UserEvent() {
            this.additionalData = new HashMap<>();
        }
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public String getPage() { return page; }
        public void setPage(String page) { this.page = page; }
        
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public Map<String, Object> getAdditionalData() { return additionalData; }
        public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }
        
        @Override
        public String toString() {
            return String.format("UserEvent{userId='%s', eventType='%s', page='%s', timestamp=%d}", 
                    userId, eventType, page, timestamp);
        }
    }
    
    public static class EventAggregation {
        private String key;
        private String userId;
        private String eventType;
        private long count;
        private long windowStart;
        private long windowEnd;
        
        public EventAggregation(String key, String userId, String eventType, long count, 
                               long windowStart, long windowEnd) {
            this.key = key;
            this.userId = userId;
            this.eventType = eventType;
            this.count = count;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }
        
        // Getters
        public String getKey() { return key; }
        public String getUserId() { return userId; }
        public String getEventType() { return eventType; }
        public long getCount() { return count; }
        public long getWindowStart() { return windowStart; }
        public long getWindowEnd() { return windowEnd; }
        
        @Override
        public String toString() {
            return String.format("EventAggregation{userId='%s', eventType='%s', count=%d, window=[%s - %s]}", 
                    userId, eventType, count, 
                    Instant.ofEpochMilli(windowStart), Instant.ofEpochMilli(windowEnd));
        }
    }
    
    public static class PageAggregation {
        private String page;
        private long pageViews;
        private long uniqueUsers;
        private double avgDuration;
        private long windowStart;
        private long windowEnd;
        
        public PageAggregation(String page, long pageViews, long uniqueUsers, double avgDuration,
                              long windowStart, long windowEnd) {
            this.page = page;
            this.pageViews = pageViews;
            this.uniqueUsers = uniqueUsers;
            this.avgDuration = avgDuration;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }
        
        // Getters
        public String getPage() { return page; }
        public long getPageViews() { return pageViews; }
        public long getUniqueUsers() { return uniqueUsers; }
        public double getAvgDuration() { return avgDuration; }
        public long getWindowStart() { return windowStart; }
        public long getWindowEnd() { return windowEnd; }
        
        @Override
        public String toString() {
            return String.format("PageAggregation{page='%s', views=%d, users=%d, avgDuration=%.2fs, window=[%s - %s]}", 
                    page, pageViews, uniqueUsers, avgDuration / 1000.0,
                    Instant.ofEpochMilli(windowStart), Instant.ofEpochMilli(windowEnd));
        }
    }
    
    public static class SessionInfo {
        private String userId;
        private String sessionId;
        private long sessionStart;
        private long sessionEnd;
        private long duration;
        private int eventCount;
        private String status; // active, ended
        
        public SessionInfo(String userId, String sessionId, long sessionStart, long sessionEnd,
                          long duration, int eventCount, String status) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.sessionStart = sessionStart;
            this.sessionEnd = sessionEnd;
            this.duration = duration;
            this.eventCount = eventCount;
            this.status = status;
        }
        
        // Getters
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public long getSessionStart() { return sessionStart; }
        public long getSessionEnd() { return sessionEnd; }
        public long getDuration() { return duration; }
        public int getEventCount() { return eventCount; }
        public String getStatus() { return status; }
        
        @Override
        public String toString() {
            return String.format("SessionInfo{userId='%s', sessionId='%s', duration=%ds, events=%d, status='%s'}", 
                    userId, sessionId, duration / 1000, eventCount, status);
        }
    }
    
    public static class MetricsInfo {
        private long timestamp;
        private long eventsPerSecond;
        private long totalEvents;
        private long windowStart;
        private long windowEnd;
        
        public MetricsInfo(long timestamp, long eventsPerSecond, long totalEvents,
                          long windowStart, long windowEnd) {
            this.timestamp = timestamp;
            this.eventsPerSecond = eventsPerSecond;
            this.totalEvents = totalEvents;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public long getEventsPerSecond() { return eventsPerSecond; }
        public long getTotalEvents() { return totalEvents; }
        public long getWindowStart() { return windowStart; }
        public long getWindowEnd() { return windowEnd; }
        
        @Override
        public String toString() {
            return String.format("MetricsInfo{eventsPerSecond=%d, totalEvents=%d, timestamp=%s}", 
                    eventsPerSecond, totalEvents, Instant.ofEpochMilli(timestamp));
        }
    }
    
    // Function implementations
    public static class JsonParserFunction implements MapFunction<String, UserEvent> {
        private static final ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        
        @Override
        public UserEvent map(String jsonString) throws Exception {
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonString);
                UserEvent event = new UserEvent();
                
                event.setUserId(jsonNode.get("userId").asText());
                event.setSessionId(jsonNode.get("sessionId").asText());
                event.setEventType(jsonNode.get("eventType").asText());
                
                // Parse timestamp
                String timestampStr = jsonNode.get("timestamp").asText();
                Instant instant = Instant.parse(timestampStr);
                event.setTimestamp(instant.toEpochMilli());
                
                // Optional fields
                if (jsonNode.has("page")) {
                    event.setPage(jsonNode.get("page").asText());
                }
                if (jsonNode.has("country")) {
                    event.setCountry(jsonNode.get("country").asText());
                }
                
                // Store additional data
                Map<String, Object> additionalData = new HashMap<>();
                jsonNode.fields().forEachRemaining(field -> {
                    String key = field.getKey();
                    if (!key.equals("userId") && !key.equals("sessionId") && 
                        !key.equals("eventType") && !key.equals("timestamp") &&
                        !key.equals("page") && !key.equals("country")) {
                        additionalData.put(key, field.getValue().asText());
                    }
                });
                event.setAdditionalData(additionalData);
                
                return event;
            } catch (Exception e) {
                LOG.error("Failed to parse JSON: {}", jsonString, e);
                // Return a default event to avoid breaking the stream
                UserEvent errorEvent = new UserEvent();
                errorEvent.setUserId("unknown");
                errorEvent.setEventType("parse_error");
                errorEvent.setTimestamp(System.currentTimeMillis());
                return errorEvent;
            }
        }
    }
    
    public static class UserEventTypeAggregationFunction 
            implements WindowFunction<UserEvent, EventAggregation, String, TimeWindow> {
        
        @Override
        public void apply(String key, TimeWindow window, Iterable<UserEvent> events, 
                         Collector<EventAggregation> out) throws Exception {
            
            long count = 0;
            String userId = null;
            String eventType = null;
            
            for (UserEvent event : events) {
                count++;
                if (userId == null) {
                    userId = event.getUserId();
                    eventType = event.getEventType();
                }
            }
            
            EventAggregation aggregation = new EventAggregation(
                    key, userId, eventType, count, window.getStart(), window.getEnd());
            out.collect(aggregation);
        }
    }
    
    public static class PageAggregationFunction 
            implements WindowFunction<UserEvent, PageAggregation, String, TimeWindow> {
        
        @Override
        public void apply(String page, TimeWindow window, Iterable<UserEvent> events, 
                         Collector<PageAggregation> out) throws Exception {
            
            long pageViews = 0;
            long totalDuration = 0;
            int durationCount = 0;
            Map<String, Boolean> uniqueUsers = new HashMap<>();
            
            for (UserEvent event : events) {
                pageViews++;
                uniqueUsers.put(event.getUserId(), true);
                
                // Calculate duration if available
                Object durationObj = event.getAdditionalData().get("duration");
                if (durationObj != null) {
                    try {
                        long duration = Long.parseLong(durationObj.toString());
                        totalDuration += duration;
                        durationCount++;
                    } catch (NumberFormatException e) {
                        // Ignore invalid duration
                    }
                }
            }
            
            double avgDuration = durationCount > 0 ? (double) totalDuration / durationCount : 0.0;
            
            PageAggregation aggregation = new PageAggregation(
                    page, pageViews, uniqueUsers.size(), avgDuration, 
                    window.getStart(), window.getEnd());
            out.collect(aggregation);
        }
    }
    
    public static class SessionAnalysisFunction 
            extends KeyedProcessFunction<String, UserEvent, SessionInfo> {
        
        private ValueState<SessionState> sessionState;
        
        @Override
        public void open(Configuration parameters) throws Exception {
            ValueStateDescriptor<SessionState> descriptor = 
                    new ValueStateDescriptor<>("session-state", SessionState.class);
            sessionState = getRuntimeContext().getState(descriptor);
        }
        
        @Override
        public void processElement(UserEvent event, Context ctx, Collector<SessionInfo> out) 
                throws Exception {
            
            SessionState currentState = sessionState.value();
            
            if (currentState == null || !currentState.sessionId.equals(event.getSessionId())) {
                // New session started
                if (currentState != null) {
                    // Emit the previous session as ended
                    SessionInfo endedSession = new SessionInfo(
                            currentState.userId, currentState.sessionId,
                            currentState.sessionStart, currentState.lastEventTime,
                            currentState.lastEventTime - currentState.sessionStart,
                            currentState.eventCount, "ended");
                    out.collect(endedSession);
                }
                
                // Start new session
                currentState = new SessionState();
                currentState.userId = event.getUserId();
                currentState.sessionId = event.getSessionId();
                currentState.sessionStart = event.getTimestamp();
                currentState.lastEventTime = event.getTimestamp();
                currentState.eventCount = 1;
            } else {
                // Continue existing session
                currentState.lastEventTime = event.getTimestamp();
                currentState.eventCount++;
            }
            
            sessionState.update(currentState);
            
            // Set timer for session timeout (30 minutes)
            ctx.timerService().registerEventTimeTimer(event.getTimestamp() + 30 * 60 * 1000);
        }
        
        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<SessionInfo> out) 
                throws Exception {
            
            SessionState currentState = sessionState.value();
            if (currentState != null && 
                timestamp - currentState.lastEventTime >= 30 * 60 * 1000) {
                
                // Session timed out
                SessionInfo timedOutSession = new SessionInfo(
                        currentState.userId, currentState.sessionId,
                        currentState.sessionStart, currentState.lastEventTime,
                        currentState.lastEventTime - currentState.sessionStart,
                        currentState.eventCount, "timeout");
                out.collect(timedOutSession);
                
                sessionState.clear();
            }
        }
        
        public static class SessionState {
            public String userId;
            public String sessionId;
            public long sessionStart;
            public long lastEventTime;
            public int eventCount;
        }
    }
    
    public static class RealTimeMetricsFunction 
            implements WindowFunction<UserEvent, MetricsInfo, String, TimeWindow> {
        
        @Override
        public void apply(String key, TimeWindow window, Iterable<UserEvent> events, 
                         Collector<MetricsInfo> out) throws Exception {
            
            long totalEvents = 0;
            for (UserEvent event : events) {
                totalEvents++;
            }
            
            long windowDurationSeconds = (window.getEnd() - window.getStart()) / 1000;
            long eventsPerSecond = windowDurationSeconds > 0 ? totalEvents / windowDurationSeconds : totalEvents;
            
            MetricsInfo metrics = new MetricsInfo(
                    System.currentTimeMillis(), eventsPerSecond, totalEvents,
                    window.getStart(), window.getEnd());
            out.collect(metrics);
        }
    }
}