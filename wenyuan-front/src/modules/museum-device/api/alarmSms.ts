import request from '@/utils/request';

export interface AlarmSmsPolicy {
  id?: string; orgId: string; name: string; enabled: boolean;
  minSeverity: 'MAJOR' | 'CRITICAL'; deviceId?: string | null; locationId?: string | null; ruleId?: string | null;
  userIds: string[]; roleIds: string[]; templateCode: string; bypassSubscription: boolean;
  cooldownSeconds: number; expirySeconds: number; maxAttempts: number;
}
export interface AlarmSmsTask {
  id: string; orgId: string; eventId: string; title: string; severity: string; userName: string; phone: string;
  status: string; attempts: number; code?: string; message?: string; requestId?: string;
  createdAt: number; updatedAt: number; expiresAt: number;
}
const base = '/museum/ingest/alarm-sms';
export const alarmSmsApi = {
  policies: (params: any) => request({ url: `${base}/policies`, method: 'get', params }),
  tasks: (params: any) => request({ url: `${base}/tasks`, method: 'get', params }),
  options: (orgId: string) => request({ url: `${base}/options`, method: 'get', params: { orgId } }),
  save: (data: AlarmSmsPolicy) => request({ url: `${base}/policies`, method: 'post', data }),
};
