import request from '@/utils/request';
import type { RfidDirection, RfidSessionType } from '../typings/rfid';

const BASE = '/collection/rfid';
/**
 * 工作站浏览器凭据使用 SameSite=Strict 的 HttpOnly Cookie，注册、上下文和刷卡登录
 * 必须走当前页面同源地址，再由 Vite/Nginx 代理到后端，不能直连 APP_BASE_URL。
 */
const WORKSTATION_BROWSER_BASE_URL = '';
let workstationClientInstanceId = '';

/** getRandomValues 在内网 HTTP 页面可用；randomUUID 只在安全上下文开放。 */
export function rfidRequestId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function workstationClientInstance(): string {
  // sessionStorage 在复制标签页时可能被克隆；窗口代次只在当前页面内复用。
  if (!workstationClientInstanceId) workstationClientInstanceId = rfidRequestId();
  return workstationClientInstanceId;
}

function workstationLeaseHeaders(headers: Record<string, string> = {}) {
  return { ...headers, 'X-RFID-Client-Instance': workstationClientInstance() };
}

export const rfidApi = {
  workstations: () => request({ url: `${BASE}/workstations`, method: 'get' }),
  saveWorkstation: (data: any) => request({ url: `${BASE}/workstations`, method: 'post', data }),
  resetToken: (id: string) => request({ url: `${BASE}/workstations/${id}/reset-token`, method: 'post' }),
  cards: () => request({ url: `${BASE}/operator-cards`, method: 'get' }),
  saveCard: (data: any) => request({ url: `${BASE}/operator-cards`, method: 'post', data }),
  updateCardStatus: (id: string, status: string, reason?: string) =>
    request({ url: `${BASE}/operator-cards/${id}/status`, method: 'post', params: { status, reason } }),
  enroll: (data: { stationCode: string; enrollmentToken: string }) =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/public/enroll`, method: 'post', data }),
  workstationContext: () =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/public/context`, method: 'get' }),
  authorizeWorkstationAccess: () =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-access`, method: 'get' }),
  workstationRuntime: () =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-runtime`, method: 'get', timeout: 10000 }),
  cardLogin: (data: { stationCode: string; cardUid: string }) =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/public/card-login`, method: 'post', data }),
  acquireWorkstationLease: (force = false) =>
    request({
      baseURL: WORKSTATION_BROWSER_BASE_URL,
      url: `${BASE}/public/lease/acquire`,
      method: 'post',
      params: { force },
      headers: workstationLeaseHeaders(),
    }),
  heartbeatWorkstationLease: () =>
    request({
      baseURL: WORKSTATION_BROWSER_BASE_URL,
      url: `${BASE}/public/lease/heartbeat`,
      method: 'post',
      headers: workstationLeaseHeaders(),
    }),
  releaseWorkstationLease: () =>
    request({
      baseURL: WORKSTATION_BROWSER_BASE_URL,
      url: `${BASE}/public/lease/release`,
      method: 'post',
      headers: workstationLeaseHeaders(),
    }),

  sikeDevices: () =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-sike/devices`, method: 'get', headers: workstationLeaseHeaders() }),
  sikeConnect: (deviceId: string) =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-sike/devices/${deviceId}/connect`, method: 'post', headers: workstationLeaseHeaders() }),
  sikeHeartbeat: (deviceId: string, connectionId: string) =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-sike/devices/${deviceId}/heartbeat`, method: 'post', data: { connectionId }, headers: workstationLeaseHeaders() }),
  sikeDisconnect: (deviceId: string, connectionId: string) =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-sike/devices/${deviceId}/disconnect`, method: 'post', data: { connectionId }, headers: workstationLeaseHeaders() }),
  sikeCommands: (deviceId: string, connectionId: string) =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-sike/devices/${deviceId}/commands`, method: 'get', params: { connectionId }, headers: workstationLeaseHeaders() }),
  sikeComplete: (deviceId: string, commandId: string, data: { connectionId: string; success: boolean; resultCode?: string; message?: string }) =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-sike/devices/${deviceId}/commands/${commandId}/complete`, method: 'post', data, headers: workstationLeaseHeaders() }),
  sikeReadBatch: (deviceId: string, sessionId: string, data: { connectionId: string; streamId: string; sequence: number; reads: Array<{ epc: string; tid?: string; antenna?: number; rssi?: number; readerSerialNumber?: string }> }) =>
    request({ baseURL: WORKSTATION_BROWSER_BASE_URL, url: `${BASE}/workstation-sike/devices/${deviceId}/scan-sessions/${sessionId}/read-batches`, method: 'post', data, headers: workstationLeaseHeaders() }),

  createSession: (data: {
    workstationId: string;
    deviceId?: string;
    type: RfidSessionType;
    businessType?: string;
    businessId?: string;
    collectionIds?: string[];
    clientRequestId: string;
    directInput?: boolean;
  }) => request({ url: `${BASE}/scan-sessions`, method: 'post', data, headers: workstationLeaseHeaders() }),
  activeSession: (workstationId: string) =>
    request({ url: `${BASE}/scan-sessions/active`, method: 'get', params: { workstationId } }),
  sessions: (params?: Record<string, unknown>) => request({ url: `${BASE}/scan-sessions`, method: 'get', params }),
  session: (id: string) => request({ url: `${BASE}/scan-sessions/${id}`, method: 'get' }),
  startSession: (id: string) =>
    request({ url: `${BASE}/scan-sessions/${id}/start`, method: 'post', headers: workstationLeaseHeaders() }),
  submitRead: (
    id: string,
    data: {
      epc: string;
      tid?: string;
      antenna?: number;
      direction?: RfidDirection;
      rssi?: number;
      sourceEventId?: string;
    },
  ) => request({ url: `${BASE}/scan-sessions/${id}/reads`, method: 'post', data }),
  stopSession: (id: string) =>
    request({ url: `${BASE}/scan-sessions/${id}/stop`, method: 'post', headers: workstationLeaseHeaders() }),
  preview: (id: string) => request({ url: `${BASE}/scan-sessions/${id}/preview`, method: 'get' }),
  finalize: (id: string, data: any, grant?: string) =>
    request({
      url: `${BASE}/scan-sessions/${id}/finalize`,
      method: 'post',
      data,
      headers: workstationLeaseHeaders(grant ? { 'X-Sensitive-Operation-Grant': grant } : {}),
    }),
  cancelSession: (id: string) =>
    request({ url: `${BASE}/scan-sessions/${id}/cancel`, method: 'post', headers: workstationLeaseHeaders() }),
  forceTerminateSession: (id: string, reason: string) =>
    request({ url: `${BASE}/scan-sessions/${id}/force-terminate`, method: 'post', params: { reason } }),

  tags: (params?: Record<string, unknown>) => request({ url: `${BASE}/tags`, method: 'get', params }),
  tagOperations: (params?: Record<string, unknown>) =>
    request({ url: `${BASE}/tag-operations`, method: 'get', params }),
  createTagJob: (data: any) =>
    request({ url: `${BASE}/tag-jobs`, method: 'post', data, headers: workstationLeaseHeaders() }),
  tagJob: (id: string) => request({ url: `${BASE}/tag-jobs/${id}`, method: 'get' }),
  writeTag: (id: string, tid: string) =>
    request({
      url: `${BASE}/tag-jobs/${id}/write`,
      method: 'post',
      data: { tid },
      headers: workstationLeaseHeaders(),
    }),
  verifyTag: (id: string, scanSessionId: string, grant?: string) =>
    request({
      url: `${BASE}/tag-jobs/${id}/verify`,
      method: 'post',
      data: { scanSessionId },
      headers: workstationLeaseHeaders(grant ? { 'X-Sensitive-Operation-Grant': grant } : {}),
    }),
  retireTag: (id: string, reason: string, grant?: string) =>
    request({
      url: `${BASE}/tags/${id}/retire`,
      method: 'post',
      data: { reason },
      headers: grant ? { 'X-Sensitive-Operation-Grant': grant } : undefined,
    }),
  verifySensitiveOperation: (data: {
    action: string;
    resourceDigest: string;
    password: string;
    captchaUuid: string;
    captchaCode: string;
  }) => request({ url: `${BASE}/sensitive-auth/verify`, method: 'post', data }),

  grants: (params?: Record<string, unknown>) => request({ url: `${BASE}/passage/grants`, method: 'get', params }),
  createGrant: (data: any) => request({ url: `${BASE}/passage/grants`, method: 'post', data }),
  revokeGrant: (id: string, reason: string) =>
    request({ url: `${BASE}/passage/grants/${id}/revoke`, method: 'post', data: { reason } }),
  passageDevices: () => request({ url: `${BASE}/passage/devices`, method: 'get' }),
  passageEvents: (params?: Record<string, unknown>) =>
    request({ url: `${BASE}/passage/events`, method: 'get', params }),
  anomalies: (params?: Record<string, unknown>) => request({ url: `${BASE}/passage/anomalies`, method: 'get', params }),
  anomaly: (id: string) => request({ url: `${BASE}/passage/anomalies/${id}`, method: 'get' }),
  processAnomaly: (id: string, data: any) =>
    request({ url: `${BASE}/passage/anomalies/${id}/process`, method: 'post', data }),
};
