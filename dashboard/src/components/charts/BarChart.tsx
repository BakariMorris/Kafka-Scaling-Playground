import React from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ChartConfiguration,
} from 'chart.js';
import { Bar } from 'react-chartjs-2';
import { BarChartData, ChartProps } from '@/types';

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend
);

interface BarChartProps extends ChartProps {
  data: BarChartData[];
  horizontal?: boolean;
  showValues?: boolean;
  colorScheme?: 'blue' | 'green' | 'red' | 'purple' | 'rainbow';
}

export const BarChart: React.FC<BarChartProps> = ({
  data,
  height = 300,
  title,
  loading,
  className = '',
  horizontal = false,
  // showValues = false, // Commented out due to plugin issues
  colorScheme = 'blue',
}) => {
  const getColors = (scheme: string, count: number): string[] => {
    const schemes = {
      blue: ['#3b82f6', '#60a5fa', '#93c5fd', '#dbeafe'],
      green: ['#10b981', '#34d399', '#6ee7b7', '#d1fae5'],
      red: ['#ef4444', '#f87171', '#fca5a5', '#fecaca'],
      purple: ['#8b5cf6', '#a78bfa', '#c4b5fd', '#e9d5ff'],
      rainbow: ['#ef4444', '#f97316', '#f59e0b', '#10b981', '#3b82f6', '#8b5cf6'],
    };
    
    const colors = schemes[scheme as keyof typeof schemes] || schemes.blue;
    return Array.from({ length: count }, (_, i) => colors[i % colors.length]);
  };

  const colors = getColors(colorScheme, data.length);
  
  const chartData = {
    labels: data.map(item => item.category),
    datasets: [
      {
        data: data.map(item => item.value),
        backgroundColor: data.map((item, index) => 
          item.color || colors[index] || colors[0]
        ),
        borderColor: data.map((item, index) => 
          item.color || colors[index] || colors[0]
        ),
        borderWidth: 1,
        borderRadius: 4,
        borderSkipped: false,
      },
    ],
  };

  const options: ChartConfiguration<'bar'>['options'] = {
    indexAxis: horizontal ? 'y' as const : 'x' as const,
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
        displayColors: false,
        callbacks: {
          label: (context) => {
            const value = context.parsed[horizontal ? 'x' : 'y'];
            const formattedValue = typeof value === 'number' 
              ? value.toLocaleString(undefined, { maximumFractionDigits: 0 })
              : value;
            return `Count: ${formattedValue}`;
          },
        },
      },
    },
    scales: {
      x: {
        beginAtZero: true,
        title: {
          display: horizontal,
          text: 'Count',
          color: '#374151',
          font: {
            size: 12,
            weight: 'bold',
          },
        },
        grid: {
          display: horizontal,
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
              return value.toLocaleString();
            }
            return value;
          },
        },
      },
      y: {
        beginAtZero: true,
        title: {
          display: !horizontal,
          text: 'Count',
          color: '#374151',
          font: {
            size: 12,
            weight: 'bold',
          },
        },
        grid: {
          display: !horizontal,
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
              return value.toLocaleString();
            }
            return value;
          },
        },
      },
    },
  };

  // Add data labels plugin if showValues is true (commented out due to type issues)
  /*
  if (showValues) {
    options.plugins = {
      ...options.plugins,
      datalabels: {
        display: true,
        color: '#374151',
        font: {
          size: 11,
          weight: 'bold',
        },
        formatter: (value: number) => {
          return value.toLocaleString();
        },
        anchor: 'end' as const,
        align: 'top' as const,
      },
    };
  }
  */

  if (loading) {
    return (
      <div className={`relative ${className}`} style={{ height }}>
        <div className="absolute inset-0 flex items-center justify-center bg-gray-50 rounded-lg">
          <div className="loading-spinner"></div>
        </div>
      </div>
    );
  }

  if (!data || data.length === 0) {
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
      <Bar data={chartData} options={options} />
    </div>
  );
};