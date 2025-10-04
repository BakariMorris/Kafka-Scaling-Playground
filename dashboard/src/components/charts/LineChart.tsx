import React, { useRef, useEffect } from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  TimeScale,
  ChartConfiguration,
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import 'chartjs-adapter-date-fns';
import { TimeSeriesData, ChartProps } from '@/types';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  TimeScale
);

interface LineChartProps extends ChartProps {
  data: TimeSeriesData[];
  showLegend?: boolean;
  showGrid?: boolean;
  animate?: boolean;
  yAxisLabel?: string;
  timeRange?: '1m' | '5m' | '15m' | '1h';
}

export const LineChart: React.FC<LineChartProps> = ({
  data,
  height = 300,
  title,
  loading,
  className = '',
  showLegend = true,
  showGrid = true,
  animate = true,
  yAxisLabel,
}) => {
  const chartRef = useRef<ChartJS<'line'>>(null);

  // Convert time series data to Chart.js format
  const chartData = {
    datasets: data.map((series, index) => ({
      label: series.name,
      data: series.data.map(point => ({
        x: point.timestamp,
        y: point.value,
      })),
      borderColor: series.color || `hsl(${index * 360 / data.length}, 70%, 50%)`,
      backgroundColor: series.color 
        ? `${series.color}20` 
        : `hsla(${index * 360 / data.length}, 70%, 50%, 0.1)`,
      borderWidth: 2,
      fill: false,
      tension: 0.4,
      pointRadius: 0,
      pointHoverRadius: 4,
      pointBackgroundColor: series.color || `hsl(${index * 360 / data.length}, 70%, 50%)`,
      pointBorderColor: '#ffffff',
      pointBorderWidth: 2,
    })),
  };

  // Chart options
  const options: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: animate ? {
      duration: 300,
      easing: 'easeInOutQuart',
    } : false,
    interaction: {
      mode: 'index' as const,
      intersect: false,
    },
    plugins: {
      legend: {
        display: showLegend,
        position: 'top' as const,
        labels: {
          usePointStyle: true,
          padding: 20,
          font: {
            size: 12,
          },
          color: '#374151',
        },
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
          bottom: 20,
        },
      },
      tooltip: {
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        titleColor: '#111827',
        bodyColor: '#374151',
        borderColor: '#e5e7eb',
        borderWidth: 1,
        padding: 12,
        cornerRadius: 8,
        displayColors: true,
        callbacks: {
          title: (context) => {
            const date = new Date(context[0].parsed.x);
            return date.toLocaleTimeString();
          },
          label: (context) => {
            const value = context.parsed.y;
            const formattedValue = typeof value === 'number' 
              ? value.toLocaleString(undefined, { maximumFractionDigits: 2 })
              : value;
            return `${context.dataset.label}: ${formattedValue}`;
          },
        },
      },
    },
    scales: {
      x: {
        type: 'time',
        time: {
          displayFormats: {
            second: 'HH:mm:ss',
            minute: 'HH:mm',
            hour: 'HH:mm',
          },
          tooltipFormat: 'HH:mm:ss',
        },
        title: {
          display: false,
        },
        grid: {
          display: showGrid,
          color: '#f3f4f6',
          lineWidth: 1,
        },
        ticks: {
          color: '#6b7280',
          font: {
            size: 11,
          },
          maxTicksLimit: 8,
        },
      },
      y: {
        beginAtZero: true,
        title: {
          display: !!yAxisLabel,
          text: yAxisLabel,
          color: '#374151',
          font: {
            size: 12,
            weight: 'bold',
          },
        },
        grid: {
          display: showGrid,
          color: '#f3f4f6',
          lineWidth: 1,
        },
        ticks: {
          color: '#6b7280',
          font: {
            size: 11,
          },
          callback: function(value) {
            if (typeof value === 'number') {
              return value.toLocaleString(undefined, { maximumFractionDigits: 1 });
            }
            return value;
          },
        },
      },
    },
  };

  // Update chart when data changes
  useEffect(() => {
    if (chartRef.current && animate) {
      chartRef.current.update('none');
    }
  }, [data, animate]);

  if (loading) {
    return (
      <div className={`relative ${className}`} style={{ height }}>
        <div className="absolute inset-0 flex items-center justify-center bg-gray-50 rounded-lg">
          <div className="loading-spinner"></div>
        </div>
      </div>
    );
  }

  if (!data || data.length === 0 || data.every(series => series.data.length === 0)) {
    return (
      <div className={`relative ${className}`} style={{ height }}>
        <div className="absolute inset-0 flex items-center justify-center bg-gray-50 rounded-lg">
          <div className="text-center">
            <div className="text-gray-400 text-lg mb-2">📊</div>
            <div className="text-gray-500 text-sm">No data available</div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={`relative ${className}`} style={{ height }}>
      <Line
        ref={chartRef}
        data={chartData}
        options={options}
      />
    </div>
  );
};