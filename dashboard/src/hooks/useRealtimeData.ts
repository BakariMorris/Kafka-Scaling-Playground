import { useState, useEffect, useCallback, useRef } from 'react';
import { useWebSocket } from './useWebSocket';
import { 
  UserEvent, 
  TransactionEvent, 
  IoTSensorEvent, 
  MetricsInfo,
  ChartDataPoint,
  TimeSeriesData,
  WebSocketMessage 
} from '@/types';

// Export types for use in other components
export type { UserEvent, TransactionEvent, IoTSensorEvent };

interface RealtimeDataState {
  userEvents: UserEvent[];
  transactionEvents: TransactionEvent[];
  iotSensorEvents: IoTSensorEvent[];
  metrics: MetricsInfo[];
  
  // Aggregated metrics
  eventsPerSecond: number;
  totalEvents: number;
  fraudCount: number;
  anomalyCount: number;
  
  // Time series data for charts
  eventRateTimeSeries: TimeSeriesData[];
  fraudRateTimeSeries: TimeSeriesData[];
  sensorValueTimeSeries: Record<string, TimeSeriesData[]>;
  
  // Real-time counters
  userEventCount: number;
  transactionEventCount: number;
  iotEventCount: number;
  
  // Status
  isReceivingData: boolean;
  lastUpdate: number;
}

interface UseRealtimeDataOptions {
  maxHistorySize?: number;
  updateInterval?: number;
  aggregationWindow?: number;
}

export const useRealtimeData = (options: UseRealtimeDataOptions = {}) => {
  const {
    maxHistorySize = 1000,
    updateInterval = 1000,
  } = options;

  const { isConnected, lastMessage, messageCount } = useWebSocket();
  
  const [data, setData] = useState<RealtimeDataState>({
    userEvents: [],
    transactionEvents: [],
    iotSensorEvents: [],
    metrics: [],
    eventsPerSecond: 0,
    totalEvents: 0,
    fraudCount: 0,
    anomalyCount: 0,
    eventRateTimeSeries: [],
    fraudRateTimeSeries: [],
    sensorValueTimeSeries: {},
    userEventCount: 0,
    transactionEventCount: 0,
    iotEventCount: 0,
    isReceivingData: false,
    lastUpdate: 0,
  });

  const lastMessageCountRef = useRef(0);
  const eventCountersRef = useRef({
    user: 0,
    transaction: 0,
    iot: 0,
    fraud: 0,
    anomaly: 0,
    lastReset: Date.now(),
  });

  // Process incoming WebSocket messages
  const processMessage = useCallback((message: WebSocketMessage) => {
    const now = Date.now();
    
    setData(prevData => {
      const newData = { ...prevData };
      
      switch (message.type) {
        case 'user_event':
          const userEvent = message.data as UserEvent;
          newData.userEvents = [userEvent, ...prevData.userEvents].slice(0, maxHistorySize);
          newData.userEventCount += 1;
          eventCountersRef.current.user += 1;
          break;
          
        case 'transaction_event':
          const transactionEvent = message.data as TransactionEvent;
          newData.transactionEvents = [transactionEvent, ...prevData.transactionEvents].slice(0, maxHistorySize);
          newData.transactionEventCount += 1;
          eventCountersRef.current.transaction += 1;
          
          if (transactionEvent.isFraud) {
            newData.fraudCount += 1;
            eventCountersRef.current.fraud += 1;
          }
          break;
          
        case 'iot_sensor_event':
          const iotEvent = message.data as IoTSensorEvent;
          newData.iotSensorEvents = [iotEvent, ...prevData.iotSensorEvents].slice(0, maxHistorySize);
          newData.iotEventCount += 1;
          eventCountersRef.current.iot += 1;
          
          if (iotEvent.isAnomaly) {
            newData.anomalyCount += 1;
            eventCountersRef.current.anomaly += 1;
          }
          
          // Update sensor time series data
          const sensorKey = `${iotEvent.location}_${iotEvent.sensorType}`;
          if (!newData.sensorValueTimeSeries[sensorKey]) {
            newData.sensorValueTimeSeries[sensorKey] = [{
              name: `${iotEvent.sensorType} (${iotEvent.location})`,
              data: [],
              color: getColorForSensor(iotEvent.sensorType),
            }];
          }
          
          const sensorSeries = newData.sensorValueTimeSeries[sensorKey][0];
          sensorSeries.data = [
            {
              timestamp: new Date(iotEvent.timestamp).getTime(),
              value: typeof iotEvent.value === 'number' ? iotEvent.value : (iotEvent.value ? 1 : 0),
              label: iotEvent.sensorId,
            },
            ...sensorSeries.data
          ].slice(0, 100); // Keep last 100 readings per sensor
          break;
          
        case 'metrics':
          const metricsData = message.data as MetricsInfo;
          newData.metrics = [metricsData, ...prevData.metrics].slice(0, maxHistorySize);
          break;
      }
      
      newData.totalEvents = newData.userEventCount + newData.transactionEventCount + newData.iotEventCount;
      newData.isReceivingData = true;
      newData.lastUpdate = now;
      
      return newData;
    });
  }, [maxHistorySize]);

  // Calculate events per second and update time series
  const updateMetrics = useCallback(() => {
    const now = Date.now();
    const timeSinceLastReset = now - eventCountersRef.current.lastReset;
    
    if (timeSinceLastReset >= updateInterval) {
      const secondsElapsed = timeSinceLastReset / 1000;
      const eventsPerSecond = (
        eventCountersRef.current.user + 
        eventCountersRef.current.transaction + 
        eventCountersRef.current.iot
      ) / secondsElapsed;
      
      const fraudRate = eventCountersRef.current.transaction > 0 
        ? (eventCountersRef.current.fraud / eventCountersRef.current.transaction) * 100 
        : 0;

      setData(prevData => {
        const newEventRatePoint: ChartDataPoint = {
          timestamp: now,
          value: Math.round(eventsPerSecond * 100) / 100,
        };
        
        const newFraudRatePoint: ChartDataPoint = {
          timestamp: now,
          value: Math.round(fraudRate * 100) / 100,
        };

        const updatedEventRateTimeSeries = prevData.eventRateTimeSeries.length > 0
          ? prevData.eventRateTimeSeries.map(series => ({
              ...series,
              data: [newEventRatePoint, ...series.data].slice(0, 60), // Keep 1 minute of data
            }))
          : [{
              name: 'Events/Second',
              data: [newEventRatePoint],
              color: '#3b82f6',
            }];

        const updatedFraudRateTimeSeries = prevData.fraudRateTimeSeries.length > 0
          ? prevData.fraudRateTimeSeries.map(series => ({
              ...series,
              data: [newFraudRatePoint, ...series.data].slice(0, 60),
            }))
          : [{
              name: 'Fraud Rate %',
              data: [newFraudRatePoint],
              color: '#ef4444',
            }];

        return {
          ...prevData,
          eventsPerSecond: Math.round(eventsPerSecond * 100) / 100,
          eventRateTimeSeries: updatedEventRateTimeSeries,
          fraudRateTimeSeries: updatedFraudRateTimeSeries,
        };
      });

      // Reset counters
      eventCountersRef.current = {
        user: 0,
        transaction: 0,
        iot: 0,
        fraud: 0,
        anomaly: 0,
        lastReset: now,
      };
    }
  }, [updateInterval]);

  // Process new messages
  useEffect(() => {
    if (lastMessage && messageCount > lastMessageCountRef.current) {
      processMessage(lastMessage);
      lastMessageCountRef.current = messageCount;
    }
  }, [lastMessage, messageCount, processMessage]);

  // Update metrics at regular intervals
  useEffect(() => {
    const interval = setInterval(updateMetrics, updateInterval);
    return () => clearInterval(interval);
  }, [updateMetrics, updateInterval]);

  // Check if data is stale
  useEffect(() => {
    const staleCheckInterval = setInterval(() => {
      const now = Date.now();
      setData(prevData => {
        if (now - prevData.lastUpdate > 5000) { // 5 seconds without updates
          return {
            ...prevData,
            isReceivingData: false,
          };
        }
        return prevData;
      });
    }, 1000);

    return () => clearInterval(staleCheckInterval);
  }, []);

  // Helper functions
  const getLatestEventsByType = useCallback((eventType: string, limit: number = 10) => {
    switch (eventType) {
      case 'user':
        return data.userEvents.slice(0, limit);
      case 'transaction':
        return data.transactionEvents.slice(0, limit);
      case 'iot':
        return data.iotSensorEvents.slice(0, limit);
      default:
        return [];
    }
  }, [data.userEvents, data.transactionEvents, data.iotSensorEvents]);

  const getFraudTransactions = useCallback((limit: number = 10) => {
    return data.transactionEvents
      .filter(event => event.isFraud)
      .slice(0, limit);
  }, [data.transactionEvents]);

  const getAnomalySensors = useCallback((limit: number = 10) => {
    return data.iotSensorEvents
      .filter(event => event.isAnomaly)
      .slice(0, limit);
  }, [data.iotSensorEvents]);

  const getEventCountByType = useCallback(() => {
    return {
      userEvents: data.userEventCount,
      transactionEvents: data.transactionEventCount,
      iotEvents: data.iotEventCount,
      total: data.totalEvents,
    };
  }, [data.userEventCount, data.transactionEventCount, data.iotEventCount, data.totalEvents]);

  return {
    ...data,
    isConnected,
    getLatestEventsByType,
    getFraudTransactions,
    getAnomalySensors,
    getEventCountByType,
    // Additional aliases for compatibility
    transactions: data.transactionEvents,
    iotSensorData: data.iotSensorEvents,
    stats: {
      totalMessages: data.totalEvents,
      messagesByTopic: {
        'user-events': data.userEventCount,
        'transaction-events': data.transactionEventCount,
        'iot-sensor-events': data.iotEventCount,
      },
      messagesPerSecond: data.eventsPerSecond,
    },
  };
};

// Helper function to get colors for different sensor types
function getColorForSensor(sensorType: string): string {
  const colorMap: Record<string, string> = {
    temperature: '#ef4444',
    humidity: '#3b82f6',
    pressure: '#10b981',
    light: '#f59e0b',
    motion: '#8b5cf6',
    air_quality: '#06b6d4',
    vibration: '#ec4899',
    sound: '#84cc16',
    power: '#f97316',
    flow_rate: '#6366f1',
  };
  
  return colorMap[sensorType] || '#6b7280';
}