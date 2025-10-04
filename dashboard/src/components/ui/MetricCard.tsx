import React from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';
import { MetricCardProps } from '@/types';

export const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  change,
  icon,
  status = 'healthy',
  className = '',
}) => {
  const formatValue = (val: string | number): string => {
    if (typeof val === 'number') {
      if (val >= 1000000) {
        return `${(val / 1000000).toFixed(1)}M`;
      } else if (val >= 1000) {
        return `${(val / 1000).toFixed(1)}K`;
      }
      return val.toLocaleString();
    }
    return val;
  };

  const getTrendIcon = () => {
    if (!change) return null;
    
    switch (change.trend) {
      case 'up':
        return <TrendingUp className="w-4 h-4 text-green-500" />;
      case 'down':
        return <TrendingDown className="w-4 h-4 text-red-500" />;
      case 'stable':
        return <Minus className="w-4 h-4 text-gray-500" />;
      default:
        return null;
    }
  };

  const getTrendColor = () => {
    if (!change) return '';
    
    switch (change.trend) {
      case 'up':
        return 'text-green-600';
      case 'down':
        return 'text-red-600';
      case 'stable':
        return 'text-gray-600';
      default:
        return '';
    }
  };

  const getStatusColor = () => {
    switch (status) {
      case 'healthy':
        return 'border-l-green-500';
      case 'warning':
        return 'border-l-yellow-500';
      case 'error':
        return 'border-l-red-500';
      default:
        return 'border-l-gray-300';
    }
  };

  return (
    <div className={`metric-card border-l-4 ${getStatusColor()} ${className}`}>
      {/* Header with title and icon */}
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-sm font-medium text-gray-600 truncate">{title}</h3>
        {icon && (
          <div className="flex-shrink-0 text-gray-400">
            {icon}
          </div>
        )}
      </div>
      
      {/* Main value */}
      <div className="mb-2">
        <div className="text-2xl font-bold text-gray-900">
          {formatValue(value)}
        </div>
      </div>
      
      {/* Change indicator */}
      {change && (
        <div className="flex items-center space-x-1">
          {getTrendIcon()}
          <span className={`text-sm font-medium ${getTrendColor()}`}>
            {Math.abs(change.value)}%
          </span>
          <span className="text-sm text-gray-500">
            vs previous period
          </span>
        </div>
      )}
      
      {/* Status indicator */}
      <div className="mt-2">
        <span className={`status-indicator ${
          status === 'healthy' ? 'status-healthy' : 
          status === 'warning' ? 'status-warning' : 
          'status-error'
        }`}>
          {status === 'healthy' ? '●' : status === 'warning' ? '⚠' : '●'} {status}
        </span>
      </div>
    </div>
  );
};