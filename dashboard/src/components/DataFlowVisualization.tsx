import React, { useEffect, useState } from 'react';
import { useRealtimeData } from '../hooks/useRealtimeData';

interface ComponentStats {
  name: string;
  type: 'producer' | 'kafka' | 'flink' | 'elasticsearch' | 'dashboard';
  status: 'active' | 'inactive' | 'warning';
  throughput: number;
  latency?: number;
  position: { x: number; y: number };
}

interface DataFlow {
  from: string;
  to: string;
  volume: number;
  latency: number;
}

const DataFlowVisualization: React.FC = () => {
  const { stats } = useRealtimeData();
  const [components, setComponents] = useState<ComponentStats[]>([
    {
      name: 'User Events Producer',
      type: 'producer',
      status: 'active',
      throughput: 0,
      position: { x: 50, y: 100 }
    },
    {
      name: 'Transaction Producer',
      type: 'producer',
      status: 'active',
      throughput: 0,
      position: { x: 50, y: 200 }
    },
    {
      name: 'IoT Sensors Producer',
      type: 'producer',
      status: 'active',
      throughput: 0,
      position: { x: 50, y: 300 }
    },
    {
      name: 'Kafka Cluster',
      type: 'kafka',
      status: 'active',
      throughput: 0,
      position: { x: 300, y: 200 }
    },
    {
      name: 'Flink JobManager',
      type: 'flink',
      status: 'active',
      throughput: 0,
      position: { x: 550, y: 150 }
    },
    {
      name: 'Flink TaskManager',
      type: 'flink',
      status: 'active',
      throughput: 0,
      position: { x: 550, y: 250 }
    },
    {
      name: 'Elasticsearch',
      type: 'elasticsearch',
      status: 'active',
      throughput: 0,
      position: { x: 800, y: 200 }
    },
    {
      name: 'Dashboard',
      type: 'dashboard',
      status: 'active',
      throughput: 0,
      position: { x: 1050, y: 200 }
    }
  ]);

  const [dataFlows, setDataFlows] = useState<DataFlow[]>([
    { from: 'User Events Producer', to: 'Kafka Cluster', volume: 0, latency: 5 },
    { from: 'Transaction Producer', to: 'Kafka Cluster', volume: 0, latency: 3 },
    { from: 'IoT Sensors Producer', to: 'Kafka Cluster', volume: 0, latency: 2 },
    { from: 'Kafka Cluster', to: 'Flink JobManager', volume: 0, latency: 10 },
    { from: 'Flink JobManager', to: 'Flink TaskManager', volume: 0, latency: 1 },
    { from: 'Flink TaskManager', to: 'Elasticsearch', volume: 0, latency: 15 },
    { from: 'Kafka Cluster', to: 'Dashboard', volume: 0, latency: 8 },
    { from: 'Elasticsearch', to: 'Dashboard', volume: 0, latency: 12 }
  ]);

  // Update component stats based on real-time data
  useEffect(() => {
    setComponents(prev => prev.map(comp => {
      let throughput = 0;
      let status: 'active' | 'inactive' | 'warning' = 'active';

      if (comp.name === 'User Events Producer') {
        throughput = stats.messagesByTopic['user-events'] || 0;
      } else if (comp.name === 'Transaction Producer') {
        throughput = stats.messagesByTopic['transaction-events'] || 0;
      } else if (comp.name === 'IoT Sensors Producer') {
        throughput = stats.messagesByTopic['iot-sensor-events'] || 0;
      } else if (comp.name === 'Kafka Cluster') {
        throughput = stats.totalMessages || 0;
      } else if (comp.name.includes('Flink')) {
        throughput = Math.round((stats.totalMessages || 0) * 0.8);
      } else if (comp.name === 'Dashboard') {
        throughput = stats.messagesPerSecond || 0;
      }

      // Determine status based on throughput
      if (throughput > 50) {
        status = 'active';
      } else if (throughput > 10) {
        status = 'warning';
      } else {
        status = 'inactive';
      }

      return { ...comp, throughput, status };
    }));

    // Update data flows
    setDataFlows(prev => prev.map(flow => ({
      ...flow,
      volume: Math.round(Math.random() * 100 + 10),
      latency: Math.round(Math.random() * 20 + 5)
    })));
  }, [stats]);

  const getComponentColor = (type: string, status: string) => {
    const baseColors = {
      producer: '#3B82F6',
      kafka: '#F59E0B',
      flink: '#8B5CF6',
      elasticsearch: '#10B981',
      dashboard: '#EF4444'
    };

    const statusModifiers = {
      active: 1,
      warning: 0.7,
      inactive: 0.4
    };

    const baseColor = baseColors[type as keyof typeof baseColors] || '#6B7280';
    const opacity = statusModifiers[status as keyof typeof statusModifiers] || 1;
    
    return `${baseColor}${Math.round(opacity * 255).toString(16).padStart(2, '0')}`;
  };

  const renderComponent = (comp: ComponentStats) => (
    <g key={comp.name}>
      {/* Component box */}
      <rect
        x={comp.position.x - 60}
        y={comp.position.y - 30}
        width="120"
        height="60"
        rx="8"
        fill={getComponentColor(comp.type, comp.status)}
        stroke="#374151"
        strokeWidth="2"
      />
      
      {/* Component name */}
      <text
        x={comp.position.x}
        y={comp.position.y - 5}
        textAnchor="middle"
        className="text-xs font-medium fill-white"
      >
        {comp.name.split(' ').map((word, i) => (
          <tspan key={i} x={comp.position.x} dy={i > 0 ? 12 : 0}>
            {word}
          </tspan>
        ))}
      </text>
      
      {/* Throughput indicator */}
      <text
        x={comp.position.x}
        y={comp.position.y + 20}
        textAnchor="middle"
        className="text-xs fill-white opacity-80"
      >
        {comp.throughput}/s
      </text>
      
      {/* Status indicator */}
      <circle
        cx={comp.position.x + 45}
        cy={comp.position.y - 20}
        r="4"
        fill={comp.status === 'active' ? '#10B981' : comp.status === 'warning' ? '#F59E0B' : '#EF4444'}
      />
    </g>
  );

  const renderDataFlow = (flow: DataFlow) => {
    const fromComp = components.find(c => c.name === flow.from);
    const toComp = components.find(c => c.name === flow.to);
    
    if (!fromComp || !toComp) return null;

    const x1 = fromComp.position.x + 60;
    const y1 = fromComp.position.y;
    const x2 = toComp.position.x - 60;
    const y2 = toComp.position.y;

    const intensity = Math.min(flow.volume / 100, 1);
    const strokeWidth = Math.max(2, intensity * 6);
    const opacity = 0.3 + intensity * 0.7;

    return (
      <g key={`${flow.from}-${flow.to}`}>
        {/* Flow line */}
        <line
          x1={x1}
          y1={y1}
          x2={x2}
          y2={y2}
          stroke="#60A5FA"
          strokeWidth={strokeWidth}
          opacity={opacity}
          markerEnd="url(#arrowhead)"
        />
        
        {/* Flow metrics */}
        <text
          x={(x1 + x2) / 2}
          y={(y1 + y2) / 2 - 10}
          textAnchor="middle"
          className="text-xs fill-blue-600 font-medium"
        >
          {flow.volume}/s
        </text>
        
        <text
          x={(x1 + x2) / 2}
          y={(y1 + y2) / 2 + 5}
          textAnchor="middle"
          className="text-xs fill-gray-500"
        >
          {flow.latency}ms
        </text>
      </g>
    );
  };

  return (
    <div className="bg-white p-6 rounded-lg shadow-lg">
      <h3 className="text-lg font-semibold text-gray-800 mb-4">
        Data Flow Architecture
      </h3>
      
      <div className="w-full overflow-x-auto">
        <svg width="1200" height="400" className="border rounded">
          {/* Arrow marker definition */}
          <defs>
            <marker
              id="arrowhead"
              markerWidth="10"
              markerHeight="7"
              refX="9"
              refY="3.5"
              orient="auto"
            >
              <polygon
                points="0 0, 10 3.5, 0 7"
                fill="#60A5FA"
              />
            </marker>
          </defs>
          
          {/* Render data flows first (behind components) */}
          {dataFlows.map(renderDataFlow)}
          
          {/* Render components */}
          {components.map(renderComponent)}
          
          {/* Legend */}
          <g transform="translate(20, 350)">
            <text className="text-sm font-medium fill-gray-700">Status:</text>
            <circle cx="60" cy="-2" r="4" fill="#10B981" />
            <text x="70" y="2" className="text-xs fill-gray-600">Active</text>
            <circle cx="120" cy="-2" r="4" fill="#F59E0B" />
            <text x="130" y="2" className="text-xs fill-gray-600">Warning</text>
            <circle cx="180" cy="-2" r="4" fill="#EF4444" />
            <text x="190" y="2" className="text-xs fill-gray-600">Inactive</text>
          </g>
        </svg>
      </div>
      
      {/* Component details */}
      <div className="mt-6 grid grid-cols-2 md:grid-cols-4 gap-4">
        {components.map(comp => (
          <div key={comp.name} className="bg-gray-50 p-3 rounded-lg">
            <div className="flex items-center justify-between mb-2">
              <h4 className="text-sm font-medium text-gray-700">
                {comp.name.split(' ')[0]}
              </h4>
              <div
                className={`w-3 h-3 rounded-full ${
                  comp.status === 'active' ? 'bg-green-500' :
                  comp.status === 'warning' ? 'bg-yellow-500' : 'bg-red-500'
                }`}
              />
            </div>
            <div className="text-xs text-gray-600">
              <div>Throughput: {comp.throughput}/s</div>
              {comp.latency && <div>Latency: {comp.latency}ms</div>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default DataFlowVisualization;