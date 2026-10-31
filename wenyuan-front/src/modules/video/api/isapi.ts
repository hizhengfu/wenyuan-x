import request from '@/utils/request';
import type { AxiosRequestConfig, AxiosResponse } from 'axios';

export interface IsupSettings {
  sdkPath: string;
  listenAddress: string;
  publicAddress: string;
  cmsPort: number;
  alarmPort: number;
  picturePort: number;
  publicCmsPort: number;
  publicAlarmPort: number;
  publicPicturePort: number;
  alarmProtocol: 'MQTT' | 'TCP';
}
export const defaultIsupSettings = (): IsupSettings => ({
  sdkPath: '',
  listenAddress: '0.0.0.0',
  publicAddress: '',
  cmsPort: 7660,
  alarmPort: 7661,
  picturePort: 7662,
  publicCmsPort: 7660,
  publicAlarmPort: 7661,
  publicPicturePort: 7662,
  alarmProtocol: 'MQTT',
});
export interface IsapiSettings {
  maxConnections: number;
  retentionDays: number;
  storagePath: string;
  revision: number;
  isup: IsupSettings;
}
export interface IsapiSettingsView {
  settings: IsapiSettings;
  storagePathLocked: boolean;
}
export interface TargetBinding {
  targetId: number;
  lineId: string;
  stationId?: string;
  sensorId?: string;
}
export interface IsapiDevice {
  registrationId: string;
  registrationKey?: string;
  hasRegistrationKey?: boolean;
  id?: string;
  name: string;
  channelId: number;
  snapshotStreamId: number;
  timeZone: string;
  snapshotIntervalSeconds: number;
  targetLossAlarmLevel: string;
  bindings: TargetBinding[];
  cameraLineId?: string;
  cameraBinding?: { lineId: string; stationId: string; cameraId: string };
}
export interface RuntimeState {
  deviceId: string;
  state: string;
  lastPacketAt?: number;
  lastSampleAt?: number;
  lastImageAt?: number;
  error?: string;
  imageError?: string;
  registeredAt?: number;
  remoteAddress?: string;
}
export interface DeviceList {
  bindingAvailable: boolean;
  cameraBindingAvailable: boolean;
  devices: IsapiDevice[];
  states: RuntimeState[];
}
export interface BridgeSample {
  id: string;
  targetId: number;
  dateTime: number;
  x?: number;
  y?: number;
}
export interface BridgeBatch {
  id: string;
  eventType: string;
  occurredAt: number;
  receivedAt: number;
  sampleCount: number;
  metricCount: number;
  status: string;
  attempts: number;
  error?: string;
  imageIds: string[];
  imageWarning?: string;
  source: string;
  lostTargetIds?: number[];
  eventState?: string;
}
export interface DataPreview {
  receivedAt?: number;
  sampleCount: number;
  samples: BridgeSample[];
}
export interface DeviceEvent {
  id: string;
  occurredAt: number;
  receivedAt: number;
  eventState?: string;
  targetIds: number[];
}
export interface IsapiImage {
  id: string;
  deviceId: string;
  batchId?: string;
  capturedAt: number;
  source: string;
  width: number;
  height: number;
}
export interface HistoryPage {
  list: BridgeSample[];
  batches: BridgeBatch[];
  nextBefore?: number;
  nextBeforeId?: string;
  hasMore: boolean;
}

async function data<T>(config: AxiosRequestConfig): Promise<T> {
  return ((await request(config)) as unknown as { data: T }).data;
}
const base = 'video/isapi';
export const getSettings = () => data<IsapiSettingsView>({ url: `${base}/config`, method: 'get' });
export const saveSettings = (settings: IsapiSettings) =>
  data<IsapiSettingsView>({ url: `${base}/config`, method: 'put', data: settings });
export const listDevices = () => data<DeviceList>({ url: `${base}/devices`, method: 'get' });
export const saveDevice = (device: IsapiDevice) =>
  data<string>({
    url: device.id ? `${base}/devices/${device.id}` : `${base}/devices`,
    method: device.id ? 'put' : 'post',
    data: device,
  });
export const deleteDevice = (id: string) => data<void>({ url: `${base}/devices/${id}`, method: 'delete' });
export const testDevice = (id: string) =>
  data<Record<string, unknown>>({ url: `${base}/devices/${id}/test`, method: 'post' });
export const captureDevice = (id: string) =>
  data<BridgeBatch>({ url: `${base}/devices/${id}/capture`, method: 'post' });
export const listImages = (id: string) => data<IsapiImage[]>({ url: `${base}/devices/${id}/images`, method: 'get' });
export const history = (
  id: string,
  params: { from: number; to: number; targetId: number; before?: number; beforeId?: string },
) => data<HistoryPage>({ url: `${base}/devices/${id}/history`, method: 'get', params });
export const dataPreview = (id: string) => data<DataPreview>({ url: `${base}/devices/${id}/preview`, method: 'get' });
export const deviceEvents = (id: string) => data<DeviceEvent[]>({ url: `${base}/devices/${id}/events`, method: 'get' });
export const imageInfo = (id: string) => data<IsapiImage>({ url: `${base}/images/${id}/info`, method: 'get' });
export async function imageBlob(id: string, signal?: AbortSignal): Promise<Blob> {
  const result = await request({
    url: `${base}/images/${id}`,
    method: 'get',
    responseType: 'blob',
    returnResponse: true,
    signal,
  } as AxiosRequestConfig & { returnResponse: boolean });
  return (result as unknown as AxiosResponse<Blob>).data;
}

export const stateLabel: Record<string, string> = {
  NEEDS_REGISTRATION: '待完善注册配置',
  WAIT_REGISTER: '等待设备主动注册',
  AUTHENTICATING: '注册认证中',
  REGISTERED: '已注册，等待数据',
  SLEEPING: '设备休眠',
  RECEIVING: '正在接收',
  ERROR: '采集异常',
  OTHER_INSTANCE: '由其他实例采集',
};
export const imageSourceLabel: Record<string, string> = {
  BRIDGE_CAPTURE: '桥梁抓拍图',
  CHANNEL_SNAPSHOT: '通道当前帧',
  EVENT: '设备事件图',
};
export function formatTime(value?: number): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
}
