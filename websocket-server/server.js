const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const { Kafka } = require('kafkajs');
const winston = require('winston');

// Configure logging
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.errors({ stack: true }),
    winston.format.json()
  ),
  defaultMeta: { service: 'websocket-server' },
  transports: [
    new winston.transports.File({ filename: 'error.log', level: 'error' }),
    new winston.transports.File({ filename: 'combined.log' }),
    new winston.transports.Console({
      format: winston.format.combine(
        winston.format.colorize(),
        winston.format.simple()
      )
    })
  ],
});

// Configuration
const config = {
  port: process.env.PORT || 3001,
  kafkaBrokers: (process.env.KAFKA_BROKERS || 'localhost:9092,localhost:9093,localhost:9094').split(','),
  corsOrigin: process.env.CORS_ORIGIN || 'http://localhost:3000',
  maxConnections: parseInt(process.env.MAX_CONNECTIONS) || 100,
  kafkaTopics: [
    'user-events',
    'transaction-events',
    'iot-sensor-events',
    'aggregated-events',
    'dashboard-metrics'
  ]
};

// Create Express app
const app = express();
const server = http.createServer(app);

// Middleware
app.use(helmet());
app.use(compression());
app.use(cors({
  origin: config.corsOrigin,
  credentials: true
}));
app.use(express.json({ limit: '1mb' }));

// Socket.IO setup
const io = socketIo(server, {
  cors: {
    origin: config.corsOrigin,
    methods: ["GET", "POST"],
    credentials: true
  },
  transports: ['websocket', 'polling'],
  maxHttpBufferSize: 1e6, // 1MB
  pingTimeout: 60000,
  pingInterval: 25000
});

// Kafka setup
const kafka = new Kafka({
  clientId: 'websocket-server',
  brokers: config.kafkaBrokers,
  retry: {
    initialRetryTime: 1000,
    retries: 5,
    maxRetryTime: 30000,
    factor: 2
  },
  connectionTimeout: 10000,
  requestTimeout: 30000
});

// Global state
let kafkaConsumer = null;
let connectedClients = new Set();
let messageStats = {
  totalMessages: 0,
  messagesByTopic: {},
  messagesPerSecond: 0,
  lastResetTime: Date.now()
};

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    connectedClients: connectedClients.size,
    messageStats: messageStats,
    uptime: process.uptime()
  });
});

// Metrics endpoint
app.get('/metrics', (req, res) => {
  res.json({
    connectedClients: connectedClients.size,
    messageStats: messageStats,
    memoryUsage: process.memoryUsage(),
    uptime: process.uptime()
  });
});

// Socket.IO connection handling
io.on('connection', (socket) => {
  connectedClients.add(socket.id);
  
  logger.info(`Client connected: ${socket.id} (Total: ${connectedClients.size})`);
  
  // Send connection acknowledgment
  socket.emit('connected', {
    clientId: socket.id,
    timestamp: Date.now(),
    serverInfo: {
      version: '1.0.0',
      topics: config.kafkaTopics
    }
  });

  // Handle client subscription to specific topics
  socket.on('subscribe', (topics) => {
    if (Array.isArray(topics)) {
      topics.forEach(topic => {
        socket.join(`topic:${topic}`);
        logger.info(`Client ${socket.id} subscribed to topic: ${topic}`);
      });
    }
  });

  // Handle client unsubscription
  socket.on('unsubscribe', (topics) => {
    if (Array.isArray(topics)) {
      topics.forEach(topic => {
        socket.leave(`topic:${topic}`);
        logger.info(`Client ${socket.id} unsubscribed from topic: ${topic}`);
      });
    }
  });

  // Handle client requesting current stats
  socket.on('requestStats', () => {
    socket.emit('stats', messageStats);
  });

  // Handle disconnection
  socket.on('disconnect', (reason) => {
    connectedClients.delete(socket.id);
    logger.info(`Client disconnected: ${socket.id} (Reason: ${reason}, Remaining: ${connectedClients.size})`);
  });

  // Handle errors
  socket.on('error', (error) => {
    logger.error(`Socket error for client ${socket.id}:`, error);
  });
});

// Kafka consumer setup
async function setupKafkaConsumer() {
  try {
    kafkaConsumer = kafka.consumer({ 
      groupId: 'websocket-server-group',
      maxWaitTimeInMs: 3000,
      maxBytes: 1024 * 1024, // 1MB
      allowAutoTopicCreation: false
    });

    await kafkaConsumer.connect();
    logger.info('Kafka consumer connected');

    // Subscribe to all configured topics
    for (const topic of config.kafkaTopics) {
      try {
        await kafkaConsumer.subscribe({ topic, fromBeginning: false });
        logger.info(`Subscribed to Kafka topic: ${topic}`);
      } catch (error) {
        logger.warn(`Failed to subscribe to topic ${topic}:`, error.message);
      }
    }

    // Start consuming messages
    await kafkaConsumer.run({
      eachMessage: async ({ topic, partition, message }) => {
        try {
          const messageValue = message.value?.toString();
          if (!messageValue) return;

          // Parse message
          let parsedMessage;
          try {
            parsedMessage = JSON.parse(messageValue);
          } catch (parseError) {
            logger.warn(`Failed to parse message from topic ${topic}:`, parseError.message);
            return;
          }

          // Update statistics
          messageStats.totalMessages++;
          messageStats.messagesByTopic[topic] = (messageStats.messagesByTopic[topic] || 0) + 1;

          // Determine message type based on topic
          let messageType;
          switch (topic) {
            case 'user-events':
              messageType = 'user_event';
              break;
            case 'transaction-events':
              messageType = 'transaction_event';
              break;
            case 'iot-sensor-events':
              messageType = 'iot_sensor_event';
              break;
            case 'aggregated-events':
              messageType = 'aggregation';
              break;
            case 'dashboard-metrics':
              messageType = 'metrics';
              break;
            default:
              messageType = 'unknown';
          }

          // Create WebSocket message
          const wsMessage = {
            type: messageType,
            data: parsedMessage,
            timestamp: Date.now(),
            topic: topic,
            partition: partition,
            offset: message.offset
          };

          // Broadcast to all connected clients
          io.emit('data', wsMessage);
          
          // Also emit to specific topic subscribers
          io.to(`topic:${topic}`).emit(messageType, parsedMessage);

          logger.debug(`Broadcasted message from topic ${topic} to ${connectedClients.size} clients`);

        } catch (error) {
          logger.error(`Error processing message from topic ${topic}:`, error);
        }
      },
    });

    logger.info('Kafka consumer is running and processing messages');

  } catch (error) {
    logger.error('Failed to setup Kafka consumer:', error);
    throw error;
  }
}

// Statistics calculation
function updateStats() {
  const now = Date.now();
  const timeDiff = (now - messageStats.lastResetTime) / 1000; // seconds
  
  if (timeDiff >= 10) { // Update every 10 seconds
    messageStats.messagesPerSecond = Math.round(messageStats.totalMessages / timeDiff * 10) / 10;
    
    // Broadcast stats to all clients
    io.emit('stats', {
      ...messageStats,
      timestamp: now,
      connectedClients: connectedClients.size
    });

    // Reset counters for next period
    messageStats.totalMessages = 0;
    messageStats.messagesByTopic = {};
    messageStats.lastResetTime = now;
  }
}

// Graceful shutdown handling
async function gracefulShutdown(signal) {
  logger.info(`Received ${signal}, shutting down gracefully...`);
  
  try {
    // Disconnect all WebSocket clients
    io.emit('server_shutdown', { message: 'Server is shutting down' });
    io.close();
    
    // Disconnect Kafka consumer
    if (kafkaConsumer) {
      await kafkaConsumer.disconnect();
      logger.info('Kafka consumer disconnected');
    }
    
    // Close HTTP server
    server.close(() => {
      logger.info('HTTP server closed');
      process.exit(0);
    });
    
    // Force exit after timeout
    setTimeout(() => {
      logger.warn('Forcing exit due to timeout');
      process.exit(1);
    }, 10000);
    
  } catch (error) {
    logger.error('Error during graceful shutdown:', error);
    process.exit(1);
  }
}

// Error handling
process.on('uncaughtException', (error) => {
  logger.error('Uncaught Exception:', error);
  gracefulShutdown('UNCAUGHT_EXCEPTION');
});

process.on('unhandledRejection', (reason, promise) => {
  logger.error('Unhandled Rejection at:', promise, 'reason:', reason);
});

// Signal handlers
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

// Start server
async function startServer() {
  try {
    // Start HTTP server
    server.listen(config.port, () => {
      logger.info(`WebSocket server listening on port ${config.port}`);
      logger.info(`CORS origin: ${config.corsOrigin}`);
      logger.info(`Max connections: ${config.maxConnections}`);
      logger.info(`Kafka brokers: ${config.kafkaBrokers.join(', ')}`);
    });

    // Set up Kafka consumer
    await setupKafkaConsumer();

    // Start statistics updates
    setInterval(updateStats, 5000); // Update every 5 seconds

    // Connection limit monitoring
    io.engine.on('connection_error', (err) => {
      logger.warn('Connection error:', err.req, err.code, err.message, err.context);
    });

    // Monitor connection count
    setInterval(() => {
      if (connectedClients.size >= config.maxConnections) {
        logger.warn(`High connection count: ${connectedClients.size}/${config.maxConnections}`);
      }
    }, 30000); // Check every 30 seconds

    logger.info('Server startup complete');

  } catch (error) {
    logger.error('Failed to start server:', error);
    process.exit(1);
  }
}

// Health monitoring
setInterval(() => {
  const memUsage = process.memoryUsage();
  const memUsageMB = {
    rss: Math.round(memUsage.rss / 1024 / 1024),
    heapTotal: Math.round(memUsage.heapTotal / 1024 / 1024),
    heapUsed: Math.round(memUsage.heapUsed / 1024 / 1024),
    external: Math.round(memUsage.external / 1024 / 1024)
  };
  
  logger.debug(`Memory usage: RSS=${memUsageMB.rss}MB, Heap=${memUsageMB.heapUsed}/${memUsageMB.heapTotal}MB, External=${memUsageMB.external}MB`);
  
  // Warn if memory usage is high
  if (memUsageMB.heapUsed > 512) {
    logger.warn(`High memory usage: ${memUsageMB.heapUsed}MB heap used`);
  }
}, 60000); // Every minute

// Start the server
startServer();