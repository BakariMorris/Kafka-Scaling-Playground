import React from 'react';
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  ChartConfiguration,
} from 'chart.js';
import { Doughnut } from 'react-chartjs-2';
import { ChartProps } from '@/types';

ChartJS.register(ArcElement, Tooltip);

interface GaugeChartProps extends Omit<ChartProps, 'data'> {
  value: number;
  min: number;
  max: number;
  unit?: string;
  thresholds?: {
    low: number;
    medium: number;
    high: number;
  };
  colors?: {
    low: string;
    medium: string;
    high: string;
    background: string;
  };
  showValue?: boolean;
  showThresholds?: boolean;
}

export const GaugeChart: React.FC<GaugeChartProps> = ({
  value,
  min,
  max,
  unit = '',
  height = 200,
  title,
  loading,
  className = '',
  thresholds = { low: 30, medium: 70, high: 90 },
  colors = {
    low: '#10b981',
    medium: '#f59e0b', 
    high: '#ef4444',
    background: '#f3f4f6',
  },
  showValue = true,
  showThresholds = true,
}) => {
  // Normalize value to 0-100 range
  const normalizedValue = Math.max(0, Math.min(100, ((value - min) / (max - min)) * 100));
  
  // Determine current status and color
  const getStatusColor = (val: number): string => {
    if (val <= thresholds.low) return colors.low;
    if (val <= thresholds.medium) return colors.medium;
    return colors.high;
  };

  const getStatusText = (val: number): string => {
    if (val <= thresholds.low) return 'Good';
    if (val <= thresholds.medium) return 'Warning';
    return 'Critical';
  };

  const currentColor = getStatusColor(normalizedValue);
  const statusText = getStatusText(normalizedValue);

  // Create gauge data
  const gaugeValue = normalizedValue;
  const remainingValue = 100 - gaugeValue;

  const chartData = {
    datasets: [
      {
        data: [gaugeValue, remainingValue],
        backgroundColor: [currentColor, colors.background],
        borderWidth: 0,
        cutout: '75%',
        circumference: 180, // Half circle
        rotation: 270, // Start from bottom
      },
    ],
  };

  const options: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false,
      },
      title: {
        display: !!title,
        text: title,
        font: {
          size: 16,
          weight: 'bold',
        },
        color: '#111827',
        padding: {
          bottom: 10,
        },
      },
      tooltip: {
        enabled: false,
      },
    },
    animation: {
      animateRotate: true,
      duration: 1000,
      easing: 'easeInOutQuart',
    },
  };

  // Center text and value plugin
  const centerContentPlugin = {
    id: 'centerContent',
    beforeDraw: (chart: ChartJS<'doughnut'>) => {
      const { width, height, ctx } = chart;
      ctx.restore();
      
      const centerX = width / 2;
      const centerY = height / 2 + 10; // Adjust for half circle
      
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      
      // Main value
      if (showValue) {
        ctx.font = 'bold 24px Inter, sans-serif';
        ctx.fillStyle = '#111827';
        const displayValue = `${value.toLocaleString()}${unit}`;
        ctx.fillText(displayValue, centerX, centerY - 10);
      }
      
      // Status text
      ctx.font = '12px Inter, sans-serif';
      ctx.fillStyle = currentColor;
      ctx.fillText(statusText, centerX, centerY + 15);
      
      // Progress percentage
      ctx.font = '10px Inter, sans-serif';
      ctx.fillStyle = '#6b7280';
      ctx.fillText(`${normalizedValue.toFixed(1)}%`, centerX, centerY + 30);
      
      ctx.save();
    },
  };

  // Threshold markers plugin
  const thresholdMarkersPlugin = {
    id: 'thresholdMarkers',
    afterDraw: (chart: ChartJS<'doughnut'>) => {
      if (!showThresholds) return;
      
      const { width, height, ctx } = chart;
      const centerX = width / 2;
      const centerY = height / 2 + 10;
      const radius = Math.min(width, height) / 2 * 0.7;
      
      ctx.save();
      
      // Draw threshold markers
      const markers = [
        { value: thresholds.low, color: colors.low },
        { value: thresholds.medium, color: colors.medium },
        { value: thresholds.high, color: colors.high },
      ];
      
      markers.forEach(marker => {
        const angle = (marker.value / 100) * Math.PI - Math.PI / 2; // Convert to radians
        const startX = centerX + Math.cos(angle) * (radius - 15);
        const startY = centerY + Math.sin(angle) * (radius - 15);
        const endX = centerX + Math.cos(angle) * (radius - 5);
        const endY = centerY + Math.sin(angle) * (radius - 5);
        
        ctx.beginPath();
        ctx.moveTo(startX, startY);
        ctx.lineTo(endX, endY);
        ctx.strokeStyle = marker.color;
        ctx.lineWidth = 2;
        ctx.stroke();
      });
      
      ctx.restore();
    },
  };

  if (loading) {
    return (
      <div className={`relative ${className}`} style={{ height }}>
        <div className="absolute inset-0 flex items-center justify-center bg-gray-50 rounded-lg">
          <div className="loading-spinner"></div>
        </div>
      </div>
    );
  }

  return (
    <div className={`relative ${className}`} style={{ height }}>
      <Doughnut 
        data={chartData} 
        options={options}
        plugins={[centerContentPlugin, thresholdMarkersPlugin]}
      />
      
      {/* Min/Max labels */}
      <div className="absolute bottom-0 left-0 right-0 flex justify-between px-4 text-xs text-gray-500">
        <span>{min}{unit}</span>
        <span>{max}{unit}</span>
      </div>
      
      {/* Threshold legend */}
      {showThresholds && (
        <div className="absolute bottom-8 left-0 right-0">
          <div className="flex justify-center space-x-4 text-xs">
            <div className="flex items-center space-x-1">
              <div className="w-2 h-2 rounded-full" style={{ backgroundColor: colors.low }}></div>
              <span className="text-gray-600">Good</span>
            </div>
            <div className="flex items-center space-x-1">
              <div className="w-2 h-2 rounded-full" style={{ backgroundColor: colors.medium }}></div>
              <span className="text-gray-600">Warning</span>
            </div>
            <div className="flex items-center space-x-1">
              <div className="w-2 h-2 rounded-full" style={{ backgroundColor: colors.high }}></div>
              <span className="text-gray-600">Critical</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};