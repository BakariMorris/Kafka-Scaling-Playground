import { useState, useMemo } from 'react';
import { FilterOptions } from '../components/filters/FilterPanel';
import { UserEvent, TransactionEvent as Transaction, IoTSensorEvent as IoTSensorData } from './useRealtimeData';

const defaultFilters: FilterOptions = {
  timeRange: '5m',
  eventTypes: [],
  countries: [],
  userIds: [],
  showAnomalies: false,
  showFraud: false,
};

export const useFilters = (
  userEvents: UserEvent[],
  transactions: Transaction[],
  iotData: IoTSensorData[]
) => {
  const [filters, setFilters] = useState<FilterOptions>(defaultFilters);
  const [isFilterPanelOpen, setIsFilterPanelOpen] = useState(false);

  // Get unique values for filter options
  const availableEventTypes = useMemo(() => {
    const types = new Set(userEvents.map(event => event.eventType));
    return Array.from(types).sort();
  }, [userEvents]);

  const availableCountries = useMemo(() => {
    const countries = new Set(userEvents.map(event => event.country));
    return Array.from(countries).sort();
  }, [userEvents]);

  const availableUserIds = useMemo(() => {
    const userIds = new Set([
      ...userEvents.map(event => event.userId),
      ...transactions.map(tx => tx.userId)
    ]);
    return Array.from(userIds).sort();
  }, [userEvents, transactions]);

  // Filter functions
  const getTimeFilterTimestamp = (timeRange: string): number => {
    const now = Date.now();
    switch (timeRange) {
      case '1m': return now - 60 * 1000;
      case '5m': return now - 5 * 60 * 1000;
      case '15m': return now - 15 * 60 * 1000;
      case '1h': return now - 60 * 60 * 1000;
      case '24h': return now - 24 * 60 * 60 * 1000;
      default: return now - 5 * 60 * 1000;
    }
  };

  const filteredUserEvents = useMemo(() => {
    const timeThreshold = getTimeFilterTimestamp(filters.timeRange);
    
    return userEvents.filter(event => {
      // Time filter
      if (Number(event.timestamp) < timeThreshold) return false;
      
      // Event type filter
      if (filters.eventTypes.length > 0 && !filters.eventTypes.includes(event.eventType)) {
        return false;
      }
      
      // Country filter
      if (filters.countries.length > 0 && !filters.countries.includes(event.country)) {
        return false;
      }
      
      // User ID filter
      if (filters.userIds.length > 0 && !filters.userIds.includes(event.userId)) {
        return false;
      }
      
      return true;
    });
  }, [userEvents, filters]);

  const filteredTransactions = useMemo(() => {
    const timeThreshold = getTimeFilterTimestamp(filters.timeRange);
    
    return transactions.filter(transaction => {
      // Time filter
      if (Number(transaction.timestamp) < timeThreshold) return false;
      
      // Amount filter
      if (filters.minAmount !== undefined && transaction.amount < filters.minAmount) {
        return false;
      }
      if (filters.maxAmount !== undefined && transaction.amount > filters.maxAmount) {
        return false;
      }
      
      // User ID filter
      if (filters.userIds.length > 0 && !filters.userIds.includes(transaction.userId)) {
        return false;
      }
      
      // Fraud filter
      if (filters.showFraud && !transaction.isFraud) {
        return false;
      }
      
      return true;
    });
  }, [transactions, filters]);

  const filteredIoTData = useMemo(() => {
    const timeThreshold = getTimeFilterTimestamp(filters.timeRange);
    
    return iotData.filter(data => {
      // Time filter
      if (Number(data.timestamp) < timeThreshold) return false;
      
      // Anomaly filter
      if (filters.showAnomalies && !data.isAnomaly) {
        return false;
      }
      
      return true;
    });
  }, [iotData, filters]);

  // Active filter count
  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (filters.timeRange !== '5m') count++;
    if (filters.eventTypes.length > 0) count++;
    if (filters.countries.length > 0) count++;
    if (filters.userIds.length > 0) count++;
    if (filters.minAmount !== undefined) count++;
    if (filters.maxAmount !== undefined) count++;
    if (filters.showAnomalies) count++;
    if (filters.showFraud) count++;
    return count;
  }, [filters]);

  // Reset filters
  const resetFilters = () => {
    setFilters(defaultFilters);
  };

  // Quick filter presets
  const applyQuickFilter = (type: 'fraud' | 'anomalies' | 'recent' | 'high-value') => {
    switch (type) {
      case 'fraud':
        setFilters(prev => ({ ...prev, showFraud: true, timeRange: '1h' }));
        break;
      case 'anomalies':
        setFilters(prev => ({ ...prev, showAnomalies: true, timeRange: '15m' }));
        break;
      case 'recent':
        setFilters(prev => ({ ...prev, timeRange: '1m' }));
        break;
      case 'high-value':
        setFilters(prev => ({ ...prev, minAmount: 1000, timeRange: '1h' }));
        break;
    }
  };

  return {
    filters,
    setFilters,
    filteredUserEvents,
    filteredTransactions,
    filteredIoTData,
    availableEventTypes,
    availableCountries,
    availableUserIds,
    activeFilterCount,
    resetFilters,
    applyQuickFilter,
    isFilterPanelOpen,
    setIsFilterPanelOpen,
  };
};