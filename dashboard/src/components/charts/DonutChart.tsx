import React from 'react';
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
  ChartConfiguration,
} from 'chart.js';
import { Doughnut } from 'react-chartjs-2';
import { PieChartData, ChartProps } from '@/types';

ChartJS.register(ArcElement, Tooltip, Legend);

interface DonutChartProps extends ChartProps {
  data: PieChartData[];
  showLabels?: boolean;
  showPercentages?: boolean;
  centerText?: string;
  centerValue?: string | number;
  colorScheme?: 'status' | 'category' | 'rainbow';
}

export const DonutChart: React.FC<DonutChartProps> = ({
  data,
  height = 300,
  title,
  loading,
  className = '',
  showLabels = true,
  showPercentages = true,
  centerText,
  centerValue,
  colorScheme = 'rainbow',
}) => {
  const getColors = (scheme: string): string[] => {
    const schemes = {
      status: ['#10b981', '#f59e0b', '#ef4444'], // green, yellow, red
      category: ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444'],
      rainbow: [
        '#ef4444', '#f97316', '#f59e0b', '#eab308', 
        '#84cc16', '#22c55e', '#10b981', '#14b8a6',
        '#06b6d4', '#0ea5e9', '#3b82f6', '#6366f1',
        '#8b5cf6', '#a855f7', '#d946ef', '#ec4899'
      ],
    };
    
    return schemes[scheme as keyof typeof schemes] || schemes.rainbow;
  };

  const colors = getColors(colorScheme);
  const total = data.reduce((sum, item) => sum + item.value, 0);

  const chartData = {
    labels: data.map(item => item.label),
    datasets: [
      {
        data: data.map(item => item.value),
        backgroundColor: data.map((item, index) => 
          item.color || colors[index % colors.length]
        ),
        borderColor: '#ffffff',
        borderWidth: 2,
        hoverBorderWidth: 3,
        hoverOffset: 8,
      },
    ],
  };

  const options: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '60%',
    plugins: {
      legend: {
        display: showLabels,
        position: 'right' as const,
        labels: {
          usePointStyle: true,
          padding: 20,
          font: {
            size: 12,
          },
          color: '#374151',
          generateLabels: (chart) => {
            const data = chart.data;
            if (data.labels && data.datasets.length) {
              return data.labels.map((label, i) => {
                const dataset = data.datasets[0];
                const value = dataset.data[i] as number;
                const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : '0';
                
                return {
                  text: showPercentages 
                    ? `${label} (${percentage}%)`
                    : `${label}: ${value.toLocaleString()}`,
                  fillStyle: Array.isArray(dataset.backgroundColor) ? dataset.backgroundColor[i] as string : dataset.backgroundColor as string,
                  strokeStyle: Array.isArray(dataset.borderColor) ? dataset.borderColor[i] as string : dataset.borderColor as string,
                  lineWidth: dataset.borderWidth as number,
                  hidden: false,
                  index: i,
                };
              });
            }
            return [];
          },
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
          label: (context) => {
            const value = context.parsed;
            const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : '0';
            const formattedValue = value.toLocaleString();
            
            return [
              `${context.label}: ${formattedValue}`,
              `Percentage: ${percentage}%`
            ];
          },
        },
      },
    },
    animation: {
      animateRotate: true,
      animateScale: false,
      duration: 1000,
      easing: 'easeInOutQuart',
    },
    interaction: {
      intersect: false,
    },
  };

  // Center text plugin
  const centerTextPlugin = {
    id: 'centerText',
    beforeDraw: (chart: ChartJS<'doughnut'>) => {
      if (!centerText && !centerValue) return;
      
      const { width, height, ctx } = chart;
      ctx.restore();
      
      const centerX = width / 2;
      const centerY = height / 2;
      
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      
      if (centerText) {
        ctx.font = 'bold 14px Inter, sans-serif';
        ctx.fillStyle = '#6b7280';
        ctx.fillText(centerText, centerX, centerY - 10);
      }
      
      if (centerValue) {
        ctx.font = 'bold 24px Inter, sans-serif';
        ctx.fillStyle = '#111827';
        const displayValue = typeof centerValue === 'number' 
          ? centerValue.toLocaleString() 
          : centerValue;
        ctx.fillText(displayValue, centerX, centerY + (centerText ? 15 : 0));
      }
      
      ctx.save();
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

  if (!data || data.length === 0 || total === 0) {
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
      <Doughnut 
        data={chartData} 
        options={options}
        plugins={[centerTextPlugin]}
      />
    </div>
  );
};