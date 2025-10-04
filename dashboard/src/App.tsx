import { useState } from 'react';
import { Activity, Database, BarChart3, Settings, Zap, Eye, GitBranch, Filter } from 'lucide-react';
import { useRealtimeData } from '@/hooks/useRealtimeData';
import { useFilters } from '@/hooks/useFilters';
import { MetricCard } from '@/components/ui/MetricCard';
import { LineChart } from '@/components/charts/LineChart';
import { BarChart } from '@/components/charts/BarChart';
import { DonutChart } from '@/components/charts/DonutChart';
import { GaugeChart } from '@/components/charts/GaugeChart';
import DataFlowVisualization from '@/components/DataFlowVisualization';
import FilterPanel from '@/components/filters/FilterPanel';

type TabType = 'overview' | 'events' | 'transactions' | 'sensors' | 'system' | 'dataflow';

function App() {
  const [activeTab, setActiveTab] = useState<TabType>('overview');
  const realtimeData = useRealtimeData();
  const filterState = useFilters(
    realtimeData.userEvents,
    realtimeData.transactions,
    realtimeData.iotSensorData
  );

  const tabs = [
    { id: 'overview', label: 'Overview', icon: BarChart3 },
    { id: 'dataflow', label: 'Data Flow', icon: GitBranch },
    { id: 'events', label: 'User Events', icon: Activity },
    { id: 'transactions', label: 'Transactions', icon: Database },
    { id: 'sensors', label: 'IoT Sensors', icon: Zap },
    { id: 'system', label: 'System Health', icon: Settings },
  ];

  // Prepare chart data using filtered data
  const eventTypeDistribution = filterState.filteredUserEvents
    .slice(0, 100)
    .reduce((acc, event) => {
      acc[event.eventType] = (acc[event.eventType] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

  const eventTypeChartData = Object.entries(eventTypeDistribution).map(([type, count]) => ({
    label: type,
    value: count as number,
  }));

  const countryDistribution = filterState.filteredUserEvents
    .slice(0, 100)
    .reduce((acc, event) => {
      acc[event.country] = (acc[event.country] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

  const countryChartData = Object.entries(countryDistribution)
    .sort((a, b) => (b[1] as number) - (a[1] as number))
    .slice(0, 10)
    .map(([country, count]) => ({
      category: country,
      value: count as number,
    }));

  const fraudByCategory = filterState.filteredTransactions
    .filter(t => t.isFraud)
    .slice(0, 50)
    .reduce((acc, transaction) => {
      acc[transaction.merchantCategory] = (acc[transaction.merchantCategory] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

  const fraudCategoryData = Object.entries(fraudByCategory).map(([category, count]) => ({
    label: category,
    value: count as number,
  }));

  const sensorAnomalies = realtimeData.iotSensorEvents
    .filter(s => s.isAnomaly)
    .slice(0, 50)
    .reduce((acc, sensor) => {
      acc[sensor.sensorType] = (acc[sensor.sensorType] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);

  const sensorAnomalyData = Object.entries(sensorAnomalies).map(([type, count]) => ({
    category: type,
    value: count as number,
  }));

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center py-4">
            <div className="flex items-center space-x-3">
              <Eye className="w-8 h-8 text-primary-600" />
              <h1 className="text-2xl font-bold text-gray-900">
                Kafka-Flink Real-Time Dashboard
              </h1>
            </div>
            
            {/* Filter and Connection Status */}
            <div className="flex items-center space-x-4">
              <button
                onClick={() => filterState.setIsFilterPanelOpen(true)}
                className="relative inline-flex items-center px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <Filter className="w-4 h-4 mr-2" />
                Filters
                {filterState.activeFilterCount > 0 && (
                  <span className="ml-2 inline-flex items-center justify-center px-2 py-1 text-xs font-bold leading-none text-white bg-blue-600 rounded-full">
                    {filterState.activeFilterCount}
                  </span>
                )}
              </button>
              <div className="flex items-center space-x-2">
                <div className={`w-3 h-3 rounded-full ${
                  realtimeData.isConnected ? 'bg-green-500 animate-pulse' : 'bg-red-500'
                }`}></div>
                <span className="text-sm text-gray-600">
                  {realtimeData.isConnected ? 'Connected' : 'Disconnected'}
                </span>
              </div>
              <div className="text-sm text-gray-500">
                Events: {realtimeData.totalEvents.toLocaleString()}
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Navigation Tabs */}
      <nav className="bg-white border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex space-x-8">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id as TabType)}
                  className={`flex items-center space-x-2 py-4 px-1 border-b-2 font-medium text-sm ${
                    activeTab === tab.id
                      ? 'border-primary-500 text-primary-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                  }`}
                >
                  <Icon className="w-5 h-5" />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeTab === 'overview' && (
          <div className="space-y-8">
            {/* Quick Filters */}
            <div className="bg-white p-4 rounded-lg shadow-sm border">
              <h3 className="text-sm font-medium text-gray-700 mb-3">Quick Filters</h3>
              <div className="flex flex-wrap gap-2">
                <button
                  onClick={() => filterState.applyQuickFilter('recent')}
                  className="px-3 py-1 text-xs bg-blue-100 text-blue-800 rounded-full hover:bg-blue-200"
                >
                  Last 1 min
                </button>
                <button
                  onClick={() => filterState.applyQuickFilter('fraud')}
                  className="px-3 py-1 text-xs bg-red-100 text-red-800 rounded-full hover:bg-red-200"
                >
                  Fraud Only
                </button>
                <button
                  onClick={() => filterState.applyQuickFilter('anomalies')}
                  className="px-3 py-1 text-xs bg-yellow-100 text-yellow-800 rounded-full hover:bg-yellow-200"
                >
                  Anomalies
                </button>
                <button
                  onClick={() => filterState.applyQuickFilter('high-value')}
                  className="px-3 py-1 text-xs bg-green-100 text-green-800 rounded-full hover:bg-green-200"
                >
                  High Value ($1000+)
                </button>
                <button
                  onClick={filterState.resetFilters}
                  className="px-3 py-1 text-xs bg-gray-100 text-gray-800 rounded-full hover:bg-gray-200"
                >
                  Clear All
                </button>
              </div>
            </div>

            {/* Key Metrics */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
              <MetricCard
                title="Filtered Events/Sec"
                value={Math.round(filterState.filteredUserEvents.length / 5)}
                icon={<Activity className="w-5 h-5" />}
                status={filterState.filteredUserEvents.length > 0 ? 'healthy' : 'warning'}
              />
              <MetricCard
                title="Filtered Events"
                value={filterState.filteredUserEvents.length}
                icon={<BarChart3 className="w-5 h-5" />}
                status="healthy"
              />
              <MetricCard
                title="Filtered Fraud"
                value={filterState.filteredTransactions.filter(t => t.isFraud).length}
                icon={<Database className="w-5 h-5" />}
                status={filterState.filteredTransactions.filter(t => t.isFraud).length > 0 ? 'warning' : 'healthy'}
              />
              <MetricCard
                title="Filtered Anomalies"
                value={filterState.filteredIoTData.filter(d => d.isAnomaly).length}
                icon={<Zap className="w-5 h-5" />}
                status={filterState.filteredIoTData.filter(d => d.isAnomaly).length > 0 ? 'warning' : 'healthy'}
              />
            </div>

            {/* Charts Row 1 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Events Per Second
                </h3>
                <LineChart
                  data={realtimeData.eventRateTimeSeries}
                  height={300}
                  yAxisLabel="Events/sec"
                  showGrid={true}
                />
              </div>
              
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Event Type Distribution
                </h3>
                <DonutChart
                  data={eventTypeChartData}
                  height={300}
                  showPercentages={true}
                  centerText="Event Types"
                  centerValue={eventTypeChartData.length}
                />
              </div>
            </div>

            {/* Charts Row 2 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Top Countries (User Events)
                </h3>
                <BarChart
                  data={countryChartData}
                  height={300}
                  colorScheme="blue"
                />
              </div>
              
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  System Load
                </h3>
                <GaugeChart
                  value={realtimeData.eventsPerSecond}
                  min={0}
                  max={100}
                  unit="/s"
                  height={300}
                  thresholds={{ low: 20, medium: 50, high: 80 }}
                />
              </div>
            </div>
          </div>
        )}

        {activeTab === 'dataflow' && (
          <div className="space-y-8">
            <DataFlowVisualization />
          </div>
        )}

        {activeTab === 'events' && (
          <div className="space-y-8">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <MetricCard
                title="User Events"
                value={realtimeData.userEventCount}
                icon={<Activity className="w-5 h-5" />}
                status="healthy"
              />
              <MetricCard
                title="Active Sessions"
                value={new Set(realtimeData.userEvents.slice(0, 100).map(e => e.sessionId)).size}
                icon={<Eye className="w-5 h-5" />}
                status="healthy"
              />
              <MetricCard
                title="Unique Users"
                value={new Set(realtimeData.userEvents.slice(0, 100).map(e => e.userId)).size}
                icon={<Database className="w-5 h-5" />}
                status="healthy"
              />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Event Types
                </h3>
                <BarChart
                  data={eventTypeChartData.map(item => ({
                    category: item.label,
                    value: item.value,
                  }))}
                  height={300}
                  colorScheme="green"
                />
              </div>
              
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Geographic Distribution
                </h3>
                <DonutChart
                  data={Object.entries(countryDistribution).map(([country, count]) => ({
                    label: country,
                    value: count as number,
                  }))}
                  height={300}
                  colorScheme="rainbow"
                />
              </div>
            </div>

            {/* Recent Events */}
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">
                Recent User Events
              </h3>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Timestamp
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        User ID
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Event Type
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Page
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Country
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {realtimeData.userEvents.slice(0, 10).map((event, index) => (
                      <tr key={index} className="hover:bg-gray-50">
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {new Date(event.timestamp).toLocaleTimeString()}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {event.userId}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800">
                            {event.eventType}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {event.page || '-'}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {event.country}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'transactions' && (
          <div className="space-y-8">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <MetricCard
                title="Total Transactions"
                value={realtimeData.transactionEventCount}
                icon={<Database className="w-5 h-5" />}
                status="healthy"
              />
              <MetricCard
                title="Fraud Detected"
                value={realtimeData.fraudCount}
                icon={<Activity className="w-5 h-5" />}
                status={realtimeData.fraudCount > 0 ? 'warning' : 'healthy'}
              />
              <MetricCard
                title="Fraud Rate"
                value={`${realtimeData.transactionEventCount > 0 
                  ? ((realtimeData.fraudCount / realtimeData.transactionEventCount) * 100).toFixed(1)
                  : 0}%`}
                icon={<BarChart3 className="w-5 h-5" />}
                status={realtimeData.fraudCount > 0 ? 'warning' : 'healthy'}
              />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Fraud Rate Over Time
                </h3>
                <LineChart
                  data={realtimeData.fraudRateTimeSeries}
                  height={300}
                  yAxisLabel="Fraud Rate %"
                />
              </div>
              
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Fraud by Merchant Category
                </h3>
                <DonutChart
                  data={fraudCategoryData}
                  height={300}
                  colorScheme="status"
                />
              </div>
            </div>

            {/* Recent Transactions */}
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">
                Recent Transactions
              </h3>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Timestamp
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Amount
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Merchant
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Risk Score
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Status
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {realtimeData.transactionEvents.slice(0, 10).map((transaction, index) => (
                      <tr key={index} className="hover:bg-gray-50">
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {new Date(transaction.timestamp).toLocaleTimeString()}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          ${transaction.amount.toFixed(2)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {transaction.merchantCategory}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {(transaction.riskScore * 100).toFixed(1)}%
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                            transaction.isFraud 
                              ? 'bg-red-100 text-red-800'
                              : transaction.status === 'approved'
                              ? 'bg-green-100 text-green-800'
                              : 'bg-yellow-100 text-yellow-800'
                          }`}>
                            {transaction.isFraud ? 'FRAUD' : transaction.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'sensors' && (
          <div className="space-y-8">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <MetricCard
                title="IoT Events"
                value={realtimeData.iotEventCount}
                icon={<Zap className="w-5 h-5" />}
                status="healthy"
              />
              <MetricCard
                title="Anomalies Detected"
                value={realtimeData.anomalyCount}
                icon={<Activity className="w-5 h-5" />}
                status={realtimeData.anomalyCount > 0 ? 'warning' : 'healthy'}
              />
              <MetricCard
                title="Active Sensors"
                value={new Set(realtimeData.iotSensorEvents.slice(0, 100).map(s => s.sensorId)).size}
                icon={<Settings className="w-5 h-5" />}
                status="healthy"
              />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Anomalies by Sensor Type
                </h3>
                <BarChart
                  data={sensorAnomalyData}
                  height={300}
                  colorScheme="red"
                />
              </div>
              
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Sensor Locations
                </h3>
                <DonutChart
                  data={Object.entries(
                    realtimeData.iotSensorEvents
                      .slice(0, 100)
                      .reduce((acc, sensor) => {
                        acc[sensor.location] = (acc[sensor.location] || 0) + 1;
                        return acc;
                      }, {} as Record<string, number>)
                  ).map(([location, count]) => ({
                    label: location.replace(/_/g, ' '),
                    value: count as number,
                  }))}
                  height={300}
                  colorScheme="category"
                />
              </div>
            </div>

            {/* Recent Sensor Events */}
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">
                Recent Sensor Events
              </h3>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Timestamp
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Sensor ID
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Type
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Value
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Location
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Status
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {realtimeData.iotSensorEvents.slice(0, 10).map((sensor, index) => (
                      <tr key={index} className="hover:bg-gray-50">
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {new Date(sensor.timestamp).toLocaleTimeString()}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {sensor.sensorId}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {sensor.sensorType}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {typeof sensor.value === 'number' 
                            ? `${sensor.value.toFixed(2)} ${sensor.unit}`
                            : sensor.value ? 'ON' : 'OFF'
                          }
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {sensor.location.replace(/_/g, ' ')}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                            sensor.isAnomaly 
                              ? 'bg-red-100 text-red-800'
                              : 'bg-green-100 text-green-800'
                          }`}>
                            {sensor.isAnomaly ? 'ANOMALY' : 'NORMAL'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'system' && (
          <div className="space-y-8">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
              <MetricCard
                title="WebSocket"
                value={realtimeData.isConnected ? "Connected" : "Disconnected"}
                icon={<Activity className="w-5 h-5" />}
                status={realtimeData.isConnected ? 'healthy' : 'error'}
              />
              <MetricCard
                title="Data Receiving"
                value={realtimeData.isReceivingData ? "Active" : "Inactive"}
                icon={<Database className="w-5 h-5" />}
                status={realtimeData.isReceivingData ? 'healthy' : 'warning'}
              />
              <MetricCard
                title="Last Update"
                value={realtimeData.lastUpdate > 0 
                  ? `${Math.round((Date.now() - realtimeData.lastUpdate) / 1000)}s ago`
                  : "Never"
                }
                icon={<Settings className="w-5 h-5" />}
                status={realtimeData.lastUpdate > 0 && (Date.now() - realtimeData.lastUpdate) < 5000 
                  ? 'healthy' : 'warning'
                }
              />
              <MetricCard
                title="Processing Rate"
                value={`${realtimeData.eventsPerSecond}/s`}
                icon={<Zap className="w-5 h-5" />}
                status={realtimeData.eventsPerSecond > 0 ? 'healthy' : 'warning'}
              />
            </div>

            {/* System Health Placeholder */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Data Processing Load
                </h3>
                <GaugeChart
                  value={realtimeData.eventsPerSecond}
                  min={0}
                  max={50}
                  unit=" events/s"
                  height={300}
                  thresholds={{ low: 10, medium: 25, high: 40 }}
                />
              </div>
              
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">
                  Event Processing Timeline
                </h3>
                <LineChart
                  data={realtimeData.eventRateTimeSeries}
                  height={300}
                  yAxisLabel="Events/Second"
                  showGrid={true}
                />
              </div>
            </div>

            {/* System Info */}
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">
                System Information
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <div className="bg-gray-50 p-4 rounded-lg">
                  <div className="text-sm font-medium text-gray-500">Total Events Processed</div>
                  <div className="text-2xl font-bold text-gray-900">
                    {realtimeData.totalEvents.toLocaleString()}
                  </div>
                </div>
                <div className="bg-gray-50 p-4 rounded-lg">
                  <div className="text-sm font-medium text-gray-500">Active Event Types</div>
                  <div className="text-2xl font-bold text-gray-900">
                    {new Set([
                      ...realtimeData.userEvents.map(e => e.eventType),
                      ...realtimeData.transactionEvents.map(e => e.status),
                      ...realtimeData.iotSensorEvents.map(e => e.sensorType)
                    ]).size}
                  </div>
                </div>
                <div className="bg-gray-50 p-4 rounded-lg">
                  <div className="text-sm font-medium text-gray-500">Data Quality</div>
                  <div className="text-2xl font-bold text-green-600">
                    {realtimeData.isReceivingData ? "Good" : "Poor"}
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Filter Panel */}
      <FilterPanel
        filters={filterState.filters}
        onFilterChange={filterState.setFilters}
        availableEventTypes={filterState.availableEventTypes}
        availableCountries={filterState.availableCountries}
        availableUserIds={filterState.availableUserIds}
        isOpen={filterState.isFilterPanelOpen}
        onClose={() => filterState.setIsFilterPanelOpen(false)}
      />
    </div>
  );
}

export default App;