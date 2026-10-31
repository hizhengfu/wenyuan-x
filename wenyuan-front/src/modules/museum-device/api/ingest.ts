import request from '@/utils/request';
import type {
  AlarmRuleVo,
  DeviceMetricBindingVo,
  DeviceVo,
  IngestConnectorVo,
  IngestHistorySyncTaskVo,
  IngestRouteMatchTestVo,
  IngestRouteVo,
  MuseumLocationManageVo,
  MuseumLocationPlanAnchorBatchVo,
  MuseumLocationPlanSaveVo,
  MuseumComparisonRequestVo,
  StandardMetricCandidateStatus,
  DeviceCandidateBatchRegistrationVo,
  DeviceCandidateDeleteVo,
} from '../typings';
import { normalizeIngestCatalog } from '../utils/ingestCatalog';

const CONFIG_BASE = '/museum/ingest/config';
const RECORD_BASE = '/museum/ingest';
const SPATIAL_BASE = '/museum/ingest/spatial';

export const ingestApi = {
  capabilities: () => request({ url: `${RECORD_BASE}/capabilities`, method: 'get' }),
  catalog: () =>
    request({ url: `${CONFIG_BASE}/catalog`, method: 'get' }).then((response: any) => ({
      ...response,
      data: normalizeIngestCatalog(response?.data),
    })),
  standardMetrics: (includeDisabled = false) =>
    request({ url: `${RECORD_BASE}/standard-metrics`, method: 'get', params: { includeDisabled } }),
  recordFilterOptions: (params: { orgId?: string } = {}) =>
    request({ url: `${RECORD_BASE}/record-filter-options`, method: 'get', params }),
  updateStandardMetricEnabled: (code: string, enabled: boolean) =>
    request({ url: `${CONFIG_BASE}/standard-metrics/${code}/enabled`, method: 'put', data: { enabled } }),
  metricCandidateSettings: () => request({ url: `${CONFIG_BASE}/metric-candidates/settings`, method: 'get' }),
  updateMetricCandidateSettings: (enabled: boolean) =>
    request({ url: `${CONFIG_BASE}/metric-candidates/settings`, method: 'put', data: { enabled } }),
  metricCandidates: (status: StandardMetricCandidateStatus = 'PENDING') =>
    request({ url: `${CONFIG_BASE}/metric-candidates`, method: 'get', params: { status } }),
  mapMetricCandidate: (id: string, metricCode: string) =>
    request({ url: `${CONFIG_BASE}/metric-candidates/${id}/map`, method: 'post', data: { metricCode } }),
  ignoreMetricCandidate: (id: string) =>
    request({ url: `${CONFIG_BASE}/metric-candidates/${id}/ignore`, method: 'post' }),
  mqttConnection: (gatewayId: string) => request({ url: `${CONFIG_BASE}/mqtt-connection/${gatewayId}`, method: 'get' }),
  updateMqttPassword: (gatewayId: string, password: string) =>
    request({ url: `${CONFIG_BASE}/mqtt-connection/${gatewayId}/password`, method: 'put', data: { password } }),
  connectors: () => request({ url: `${CONFIG_BASE}/connectors`, method: 'get' }),
  saveConnector: (vo: IngestConnectorVo) => request({ url: `${CONFIG_BASE}/connectors`, method: 'post', data: vo }),
  deleteConnector: (id: string) => request({ url: `${CONFIG_BASE}/connectors/${id}`, method: 'delete' }),
  requestConnectorProbe: (id: string) => request({ url: `${CONFIG_BASE}/connectors/${id}/probe`, method: 'post' }),
  connectorProbe: (id: string) => request({ url: `${CONFIG_BASE}/connectors/${id}/probe`, method: 'get' }),
  routes: (gatewayId: string) => request({ url: `${CONFIG_BASE}/routes/${gatewayId}`, method: 'get' }),
  saveRoute: (vo: IngestRouteVo) => request({ url: `${CONFIG_BASE}/routes`, method: 'post', data: vo }),
  matchRoute: (vo: IngestRouteMatchTestVo) =>
    request({ url: `${CONFIG_BASE}/routes/match-test`, method: 'post', data: vo }),
  deleteRoute: (id: string) => request({ url: `${CONFIG_BASE}/routes/${id}`, method: 'delete' }),
  devices: (gatewayId?: string, orgId?: string) =>
    request({ url: `${CONFIG_BASE}/devices`, method: 'get', params: { gatewayId, orgId } }),
  devicePage: (params: Record<string, unknown>) =>
    request({ url: `${CONFIG_BASE}/devices/page`, method: 'get', params }),
  saveDevice: (vo: DeviceVo) => request({ url: `${CONFIG_BASE}/devices`, method: 'post', data: vo }),
  deviceLocationChanges: (id: string) =>
    request({ url: `${CONFIG_BASE}/devices/${id}/location-changes`, method: 'get' }),
  deleteDevice: (id: string) => request({ url: `${CONFIG_BASE}/devices/${id}`, method: 'delete' }),
  metricBindings: (deviceId: string) =>
    request({ url: `${CONFIG_BASE}/device-metric-bindings/${deviceId}`, method: 'get' }),
  saveMetricBinding: (vo: DeviceMetricBindingVo) =>
    request({ url: `${CONFIG_BASE}/device-metric-bindings`, method: 'post', data: vo }),
  updateMetricBindingEnabled: (id: string, enabled: boolean) =>
    request({ url: `${CONFIG_BASE}/device-metric-bindings/${id}/enabled`, method: 'put', data: { enabled } }),
  deleteMetricBinding: (id: string) =>
    request({ url: `${CONFIG_BASE}/device-metric-bindings/${id}`, method: 'delete' }),
  deviceCandidates: (params: Record<string, unknown>) =>
    request({ url: `${CONFIG_BASE}/device-candidates`, method: 'get', params }),
  registerDeviceCandidate: (id: string, vo: DeviceVo) =>
    request({ url: `${CONFIG_BASE}/device-candidates/${id}/register`, method: 'post', data: vo }),
  registerDeviceCandidates: (vo: DeviceCandidateBatchRegistrationVo) =>
    request({ url: `${CONFIG_BASE}/device-candidates/register-batch`, method: 'post', data: vo }),
  deleteDeviceCandidates: (vo: DeviceCandidateDeleteVo) =>
    request({ url: `${CONFIG_BASE}/device-candidates`, method: 'delete', data: vo }),
  ignoreDeviceCandidate: (id: string) =>
    request({ url: `${CONFIG_BASE}/device-candidates/${id}/ignore`, method: 'post' }),
  locations: (gatewayId: string) => request({ url: `${CONFIG_BASE}/locations/${gatewayId}`, method: 'get' }),
  locationCatalog: (params: Record<string, unknown>) =>
    request({ url: `${CONFIG_BASE}/locations`, method: 'get', params }),
  syncLocations: (orgId: string) => request({ url: `${CONFIG_BASE}/locations/sync`, method: 'post', data: { orgId } }),
  saveLocation: (vo: MuseumLocationManageVo) => request({ url: `${CONFIG_BASE}/locations`, method: 'post', data: vo }),
  updateLocationEnabled: (id: string, enabled: boolean) =>
    request({ url: `${CONFIG_BASE}/locations/${id}/enabled`, method: 'put', data: { enabled } }),
  spatialLocations: (
    params: { orgId?: string; locationId?: string; sourceCode?: string; sourceLocationId?: string } = {},
  ) => request({ url: `${SPATIAL_BASE}/locations`, method: 'get', params }),
  locationPlans: (locationId: string) =>
    request({ url: `${SPATIAL_BASE}/plans`, method: 'get', params: { locationId } }),
  locationPlanView: (id: string) => request({ url: `${SPATIAL_BASE}/plans/${id}/view`, method: 'get' }),
  saveLocationPlan: (vo: MuseumLocationPlanSaveVo) =>
    request({ url: `${SPATIAL_BASE}/plans`, method: 'post', data: vo }),
  saveLocationPlanAnchors: (id: string, vo: MuseumLocationPlanAnchorBatchVo) =>
    request({ url: `${SPATIAL_BASE}/plans/${id}/anchors`, method: 'put', data: vo }),
  deleteLocationPlan: (id: string) => request({ url: `${SPATIAL_BASE}/plans/${id}`, method: 'delete' }),
  problemMessages: (params: Record<string, unknown>) =>
    request({ url: `${CONFIG_BASE}/inbox/problems`, method: 'get', params }),
  problemMessage: (id: string) => request({ url: `${CONFIG_BASE}/inbox/${id}`, method: 'get' }),
  deadMessages: (limit = 100) => request({ url: `${CONFIG_BASE}/inbox/dead`, method: 'get', params: { limit } }),
  replayInbox: (id: string) => request({ url: `${CONFIG_BASE}/inbox/${id}/replay`, method: 'post' }),
  deleteProblemMessages: (data: { ids?: string[]; all?: boolean }) =>
    request({ url: `${CONFIG_BASE}/inbox/problems/delete`, method: 'post', data }),
  historySyncTasks: (params: Record<string, unknown>) =>
    request({ url: `${CONFIG_BASE}/history-sync-tasks`, method: 'get', params }),
  createHistorySyncTask: (vo: IngestHistorySyncTaskVo) =>
    request({ url: `${CONFIG_BASE}/history-sync-tasks`, method: 'post', data: vo }),
  deleteHistorySyncTask: (id: string) => request({ url: `${CONFIG_BASE}/history-sync-tasks/${id}`, method: 'delete' }),
  records: (params: Record<string, unknown> & { from?: number; to?: number }) =>
    request({ url: `${RECORD_BASE}/records`, method: 'get', params }),
  recordWindow: (params: Record<string, unknown> & { from?: number; to?: number }) =>
    request({ url: `${RECORD_BASE}/records/window`, method: 'get', params }),
  recordSeries: (params: Record<string, unknown> & { from: number; to: number }) =>
    request({ url: `${RECORD_BASE}/records/series`, method: 'get', params }),
  analysisMetricOptions: (params: { deviceId: string; from: number; to: number; orgId?: string }) =>
    request({ url: `${RECORD_BASE}/analysis/options`, method: 'get', params }),
  compareAnalysis: (data: MuseumComparisonRequestVo) =>
    request({ url: `${RECORD_BASE}/analysis/comparison`, method: 'post', data }),
  latestRecords: (params: Record<string, unknown>) =>
    request({ url: `${RECORD_BASE}/latest-records`, method: 'get', params }),
  alarmRules: (params: Record<string, unknown>) =>
    request({ url: `${CONFIG_BASE}/alarm-rules`, method: 'get', params }),
  saveAlarmRule: (vo: AlarmRuleVo) => request({ url: `${CONFIG_BASE}/alarm-rules`, method: 'post', data: vo }),
  setAlarmRuleRuntimeStatus: (id: string, status: 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED') =>
    request({ url: `${CONFIG_BASE}/alarm-rules/${id}/runtime-status`, method: 'post', data: { status } }),
  deleteAlarmRule: (id: string) => request({ url: `${CONFIG_BASE}/alarm-rules/${id}`, method: 'delete' }),
  alarmRuleVersion: (id: string) => request({ url: `${CONFIG_BASE}/alarm-rule-versions/${id}`, method: 'get' }),
  locationReference: (id: string) => request({ url: `${CONFIG_BASE}/locations/${id}/reference`, method: 'get' }),
  deviceReference: (id: string) => request({ url: `${CONFIG_BASE}/devices/${id}/reference`, method: 'get' }),
  alarmDetail: (id: string) => request({ url: `${CONFIG_BASE}/alarms/${id}`, method: 'get' }),
  handleAlarm: (id: string, data: { action: string; reason: string }) =>
    request({ url: `${CONFIG_BASE}/alarms/${id}/handling`, method: 'post', data }),
  alarms: (params: Record<string, unknown>) => request({ url: `${CONFIG_BASE}/alarms`, method: 'get', params }),
  alarmWindow: (params: Record<string, unknown>) => request({ url: `${CONFIG_BASE}/alarms/window`, method: 'get', params }),
};
