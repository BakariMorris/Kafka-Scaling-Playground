import React from 'react';
import { Filter, X, Calendar, MapPin, User, DollarSign } from 'lucide-react';

export interface FilterOptions {
  timeRange: '1m' | '5m' | '15m' | '1h' | '24h';
  eventTypes: string[];
  countries: string[];
  minAmount?: number;
  maxAmount?: number;
  userIds: string[];
  showAnomalies: boolean;
  showFraud: boolean;
}

interface FilterPanelProps {
  filters: FilterOptions;
  onFilterChange: (filters: FilterOptions) => void;
  availableEventTypes: string[];
  availableCountries: string[];
  availableUserIds: string[];
  isOpen: boolean;
  onClose: () => void;
}

const FilterPanel: React.FC<FilterPanelProps> = ({
  filters,
  onFilterChange,
  availableEventTypes,
  availableCountries,
  availableUserIds,
  isOpen,
  onClose,
}) => {
  const timeRangeOptions = [
    { value: '1m', label: 'Last 1 minute' },
    { value: '5m', label: 'Last 5 minutes' },
    { value: '15m', label: 'Last 15 minutes' },
    { value: '1h', label: 'Last 1 hour' },
    { value: '24h', label: 'Last 24 hours' },
  ];

  const handleEventTypeToggle = (eventType: string) => {
    const newEventTypes = filters.eventTypes.includes(eventType)
      ? filters.eventTypes.filter(t => t !== eventType)
      : [...filters.eventTypes, eventType];
    
    onFilterChange({ ...filters, eventTypes: newEventTypes });
  };

  const handleCountryToggle = (country: string) => {
    const newCountries = filters.countries.includes(country)
      ? filters.countries.filter(c => c !== country)
      : [...filters.countries, country];
    
    onFilterChange({ ...filters, countries: newCountries });
  };

  const handleUserIdToggle = (userId: string) => {
    const newUserIds = filters.userIds.includes(userId)
      ? filters.userIds.filter(id => id !== userId)
      : [...filters.userIds, userId];
    
    onFilterChange({ ...filters, userIds: newUserIds });
  };

  const clearAllFilters = () => {
    onFilterChange({
      timeRange: '5m',
      eventTypes: [],
      countries: [],
      userIds: [],
      showAnomalies: false,
      showFraud: false,
    });
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-6 border-b">
          <div className="flex items-center space-x-2">
            <Filter className="w-5 h-5 text-blue-600" />
            <h2 className="text-xl font-semibold text-gray-900">Filters & Settings</h2>
          </div>
          <button
            onClick={onClose}
            className="text-gray-500 hover:text-gray-700"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="p-6 space-y-6">
          {/* Time Range */}
          <div>
            <div className="flex items-center space-x-2 mb-3">
              <Calendar className="w-4 h-4 text-gray-600" />
              <label className="text-sm font-medium text-gray-700">Time Range</label>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
              {timeRangeOptions.map(option => (
                <button
                  key={option.value}
                  onClick={() => onFilterChange({ ...filters, timeRange: option.value as any })}
                  className={`px-3 py-2 text-sm rounded-md border ${
                    filters.timeRange === option.value
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>

          {/* Event Types */}
          <div>
            <div className="flex items-center space-x-2 mb-3">
              <User className="w-4 h-4 text-gray-600" />
              <label className="text-sm font-medium text-gray-700">Event Types</label>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {availableEventTypes.map(eventType => (
                <label
                  key={eventType}
                  className="flex items-center space-x-2 p-2 border rounded-md hover:bg-gray-50 cursor-pointer"
                >
                  <input
                    type="checkbox"
                    checked={filters.eventTypes.includes(eventType)}
                    onChange={() => handleEventTypeToggle(eventType)}
                    className="text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-700">{eventType}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Countries */}
          <div>
            <div className="flex items-center space-x-2 mb-3">
              <MapPin className="w-4 h-4 text-gray-600" />
              <label className="text-sm font-medium text-gray-700">Countries</label>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2 max-h-40 overflow-y-auto">
              {availableCountries.slice(0, 20).map(country => (
                <label
                  key={country}
                  className="flex items-center space-x-2 p-2 border rounded-md hover:bg-gray-50 cursor-pointer"
                >
                  <input
                    type="checkbox"
                    checked={filters.countries.includes(country)}
                    onChange={() => handleCountryToggle(country)}
                    className="text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-700">{country}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Amount Range */}
          <div>
            <div className="flex items-center space-x-2 mb-3">
              <DollarSign className="w-4 h-4 text-gray-600" />
              <label className="text-sm font-medium text-gray-700">Transaction Amount Range</label>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-600 mb-1">Min Amount</label>
                <input
                  type="number"
                  value={filters.minAmount || ''}
                  onChange={(e) => onFilterChange({
                    ...filters,
                    minAmount: e.target.value ? parseFloat(e.target.value) : undefined
                  })}
                  placeholder="0"
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">Max Amount</label>
                <input
                  type="number"
                  value={filters.maxAmount || ''}
                  onChange={(e) => onFilterChange({
                    ...filters,
                    maxAmount: e.target.value ? parseFloat(e.target.value) : undefined
                  })}
                  placeholder="No limit"
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                />
              </div>
            </div>
          </div>

          {/* Special Filters */}
          <div>
            <label className="text-sm font-medium text-gray-700 mb-3 block">Special Filters</label>
            <div className="space-y-2">
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={filters.showAnomalies}
                  onChange={(e) => onFilterChange({ ...filters, showAnomalies: e.target.checked })}
                  className="text-blue-600 focus:ring-blue-500"
                />
                <span className="text-sm text-gray-700">Show only anomalies</span>
              </label>
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={filters.showFraud}
                  onChange={(e) => onFilterChange({ ...filters, showFraud: e.target.checked })}
                  className="text-blue-600 focus:ring-blue-500"
                />
                <span className="text-sm text-gray-700">Show only fraud transactions</span>
              </label>
            </div>
          </div>

          {/* User IDs (limited to first 10 for demo) */}
          {availableUserIds.length > 0 && (
            <div>
              <label className="text-sm font-medium text-gray-700 mb-3 block">Specific Users (showing first 10)</label>
              <div className="grid grid-cols-2 md:grid-cols-5 gap-2 max-h-32 overflow-y-auto">
                {availableUserIds.slice(0, 10).map(userId => (
                  <label
                    key={userId}
                    className="flex items-center space-x-2 p-2 border rounded-md hover:bg-gray-50 cursor-pointer text-xs"
                  >
                    <input
                      type="checkbox"
                      checked={filters.userIds.includes(userId)}
                      onChange={() => handleUserIdToggle(userId)}
                      className="text-blue-600 focus:ring-blue-500"
                    />
                    <span className="text-gray-700 truncate">{userId.slice(0, 8)}...</span>
                  </label>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between p-6 border-t bg-gray-50">
          <button
            onClick={clearAllFilters}
            className="px-4 py-2 text-sm text-gray-600 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
          >
            Clear All Filters
          </button>
          <button
            onClick={onClose}
            className="px-6 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700"
          >
            Apply Filters
          </button>
        </div>
      </div>
    </div>
  );
};

export default FilterPanel;