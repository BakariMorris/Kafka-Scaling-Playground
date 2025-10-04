// Event types
export interface UserEvent {
  timestamp: string;
  userId: string;
  sessionId: string;
  eventType: string;
  page?: string;
  country: string;
  location: {
    lat: number;
    lon: number;
  };
  userAgent: string;
  deviceType: string;
  browser: string;
  isNewUser: boolean;
  referrer: string;
  duration?: number;
  loadTime?: number;
}

export interface TransactionEvent {
  timestamp: string;
  transactionId: string;
  userId: string;
  amount: number;
  currency: string;
  merchantId: string;
  merchantName: string;
  merchantCategory: string;
  paymentMethod: string;
  cardNumber: string;
  country: string;
  location: {
    lat: number;
    lon: number;
  };
  riskScore: number;
  status: 'approved' | 'declined' | 'pending_review';
  isFraud: boolean;
  processingTime: number;
}

export interface IoTSensorEvent {
  timestamp: string;
  sensorId: string;
  sensorType: string;
  value: number | boolean;
  unit: string;
  location: string;
  coordinates: {
    lat: number;
    lon: number;
  };
  batteryLevel: number;
  signalStrength: number;
  dataQuality: number;
  isAnomaly: boolean;
  deviceInfo: {
    manufacturer: string;
    model: string;
    firmwareVersion: string;
    installDate: string;
    calibrationDate: string;
    maintenanceDue: string;
  };
}

// Aggregated data types
export interface EventAggregation {
  key: string;
  userId: string;
  eventType: string;
  count: number;
  windowStart: number;
  windowEnd: number;
}

export interface PageAggregation {
  page: string;
  pageViews: number;
  uniqueUsers: number;
  avgDuration: number;
  windowStart: number;
  windowEnd: number;
}

export interface SessionInfo {
  userId: string;
  sessionId: string;
  sessionStart: number;
  sessionEnd: number;
  duration: number;
  eventCount: number;
  status: 'active' | 'ended' | 'timeout';
}

export interface MetricsInfo {
  timestamp: number;
  eventsPerSecond: number;
  totalEvents: number;
  windowStart: number;
  windowEnd: number;
}

// System health types
export interface ServiceHealth {
  name: string;
  status: 'healthy' | 'warning' | 'error';
  uptime: number;
  lastCheck: string;
  metadata?: Record<string, any>;
}

export interface KafkaMetrics {
  brokers: number;
  topics: number;
  partitions: number;
  totalMessages: number;
  messagesPerSecond: number;
  lagByTopic: Record<string, number>;
  brokerStats: Array<{
    id: number;
    host: string;
    port: number;
    status: 'online' | 'offline';
  }>;
}

export interface FlinkMetrics {
  jobManager: {
    status: 'running' | 'stopped';
    uptime: number;
    memory: {
      used: number;
      total: number;
    };
  };
  taskManagers: number;
  totalSlots: number;
  availableSlots: number;
  runningJobs: Array<{
    id: string;
    name: string;
    status: 'running' | 'failed' | 'finished';
    startTime: number;
    duration: number;
  }>;
  checkpointStats: {
    completed: number;
    failed: number;
    lastCompleted: number;
    duration: number;
  };
}

export interface ElasticsearchMetrics {
  clusterHealth: 'green' | 'yellow' | 'red';
  nodes: number;
  indices: number;
  totalDocuments: number;
  storageSize: number;
  indexingRate: number;
  searchRate: number;
  indexStats: Array<{
    name: string;
    documents: number;
    size: number;
    health: 'green' | 'yellow' | 'red';
  }>;
}

// Chart data types
export interface ChartDataPoint {
  timestamp: number;
  value: number;
  label?: string;
}

export interface TimeSeriesData {
  name: string;
  data: ChartDataPoint[];
  color?: string;
}

export interface PieChartData {
  label: string;
  value: number;
  color?: string;
}

export interface BarChartData {
  category: string;
  value: number;
  color?: string;
}

// WebSocket message types
export interface WebSocketMessage {
  type: 'user_event' | 'transaction_event' | 'iot_sensor_event' | 'aggregation' | 'metrics' | 'health';
  data: any;
  timestamp: number;
}

// Dashboard state types
export interface DashboardState {
  isConnected: boolean;
  lastUpdate: number;
  eventsReceived: number;
  errors: string[];
}

// Filter types
export interface FilterOptions {
  timeRange: '1m' | '5m' | '15m' | '1h' | '6h' | '24h';
  eventTypes: string[];
  countries: string[];
  sensorTypes: string[];
  riskLevels: string[];
}

// Component props types
export interface ChartProps {
  data: any;
  height?: number;
  width?: number;
  className?: string;
  title?: string;
  loading?: boolean;
}

export interface MetricCardProps {
  title: string;
  value: string | number;
  change?: {
    value: number;
    trend: 'up' | 'down' | 'stable';
  };
  icon?: React.ReactNode;
  status?: 'healthy' | 'warning' | 'error';
  className?: string;
}