import { useEffect, useRef, useState, useCallback } from 'react';
import { io, Socket } from 'socket.io-client';
import { WebSocketMessage } from '@/types';

interface UseWebSocketOptions {
  url?: string;
  autoConnect?: boolean;
  reconnectAttempts?: number;
  reconnectDelay?: number;
}

interface UseWebSocketReturn {
  socket: Socket | null;
  isConnected: boolean;
  isConnecting: boolean;
  error: string | null;
  lastMessage: WebSocketMessage | null;
  messageCount: number;
  connect: () => void;
  disconnect: () => void;
  emit: (event: string, data: any) => void;
  subscribe: (event: string, callback: (data: any) => void) => () => void;
}

export const useWebSocket = (options: UseWebSocketOptions = {}): UseWebSocketReturn => {
  const {
    url = 'http://localhost:3001',
    autoConnect = true,
    reconnectAttempts = 5,
    reconnectDelay = 1000,
  } = options;

  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);
  const [messageCount, setMessageCount] = useState(0);

  const socketRef = useRef<Socket | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const connect = useCallback(() => {
    if (socketRef.current?.connected) {
      console.log('WebSocket already connected');
      return;
    }

    setIsConnecting(true);
    setError(null);

    try {
      const socket = io(url, {
        transports: ['websocket', 'polling'],
        timeout: 5000,
        forceNew: true,
      });

      socket.on('connect', () => {
        console.log('WebSocket connected:', socket.id);
        setIsConnected(true);
        setIsConnecting(false);
        setError(null);
        reconnectAttemptsRef.current = 0;
      });

      socket.on('disconnect', (reason) => {
        console.log('WebSocket disconnected:', reason);
        setIsConnected(false);
        setIsConnecting(false);
        
        // Auto-reconnect for certain disconnect reasons
        if (reason === 'io server disconnect') {
          setError('Server disconnected the connection');
        } else if (reason === 'transport close' || reason === 'transport error') {
          attemptReconnect();
        }
      });

      socket.on('connect_error', (err) => {
        console.error('WebSocket connection error:', err);
        setIsConnecting(false);
        setError(`Connection failed: ${err.message}`);
        attemptReconnect();
      });

      // Handle real-time data messages
      socket.on('data', (message: WebSocketMessage) => {
        setLastMessage(message);
        setMessageCount(prev => prev + 1);
      });

      // Handle specific event types
      socket.on('user_event', (data) => {
        setLastMessage({ type: 'user_event', data, timestamp: Date.now() });
        setMessageCount(prev => prev + 1);
      });

      socket.on('transaction_event', (data) => {
        setLastMessage({ type: 'transaction_event', data, timestamp: Date.now() });
        setMessageCount(prev => prev + 1);
      });

      socket.on('iot_sensor_event', (data) => {
        setLastMessage({ type: 'iot_sensor_event', data, timestamp: Date.now() });
        setMessageCount(prev => prev + 1);
      });

      socket.on('aggregation', (data) => {
        setLastMessage({ type: 'aggregation', data, timestamp: Date.now() });
        setMessageCount(prev => prev + 1);
      });

      socket.on('metrics', (data) => {
        setLastMessage({ type: 'metrics', data, timestamp: Date.now() });
        setMessageCount(prev => prev + 1);
      });

      socket.on('health', (data) => {
        setLastMessage({ type: 'health', data, timestamp: Date.now() });
        setMessageCount(prev => prev + 1);
      });

      socketRef.current = socket;
    } catch (err) {
      console.error('Failed to create WebSocket connection:', err);
      setIsConnecting(false);
      setError('Failed to initialize connection');
    }
  }, [url]);

  const attemptReconnect = useCallback(() => {
    if (reconnectAttemptsRef.current >= reconnectAttempts) {
      setError(`Failed to reconnect after ${reconnectAttempts} attempts`);
      return;
    }

    reconnectAttemptsRef.current += 1;
    const delay = reconnectDelay * Math.pow(2, reconnectAttemptsRef.current - 1); // Exponential backoff

    console.log(`Attempting to reconnect (${reconnectAttemptsRef.current}/${reconnectAttempts}) in ${delay}ms`);

    reconnectTimeoutRef.current = setTimeout(() => {
      connect();
    }, delay);
  }, [connect, reconnectAttempts, reconnectDelay]);

  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    if (socketRef.current) {
      socketRef.current.disconnect();
      socketRef.current = null;
    }

    setIsConnected(false);
    setIsConnecting(false);
    setError(null);
    reconnectAttemptsRef.current = 0;
  }, []);

  const emit = useCallback((event: string, data: any) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit(event, data);
    } else {
      console.warn('Cannot emit: WebSocket not connected');
    }
  }, []);

  const subscribe = useCallback((event: string, callback: (data: any) => void) => {
    if (!socketRef.current) {
      console.warn('Cannot subscribe: WebSocket not initialized');
      return () => {};
    }

    socketRef.current.on(event, callback);

    // Return unsubscribe function
    return () => {
      if (socketRef.current) {
        socketRef.current.off(event, callback);
      }
    };
  }, []);

  // Auto-connect on mount
  useEffect(() => {
    if (autoConnect) {
      connect();
    }

    // Cleanup on unmount
    return () => {
      disconnect();
    };
  }, [autoConnect, connect, disconnect]);

  // Handle page visibility changes
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden) {
        // Page is hidden, we could pause reconnection attempts
        console.log('Page hidden, WebSocket behavior unchanged');
      } else {
        // Page is visible, ensure connection is active
        if (!socketRef.current?.connected && !isConnecting) {
          console.log('Page visible, checking WebSocket connection');
          connect();
        }
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [connect, isConnecting]);

  return {
    socket: socketRef.current,
    isConnected,
    isConnecting,
    error,
    lastMessage,
    messageCount,
    connect,
    disconnect,
    emit,
    subscribe,
  };
};