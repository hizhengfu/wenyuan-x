import request from '@/utils/request';
const BASE = '/collection/safety-task';
export const safetyTaskApi = {
  options: () => request({ url: `${BASE}/options`, method: 'get' }),
  page: (params: Record<string, any>) => request({ url: BASE, method: 'get', params }),
  context: (orgId: string) => request({ url: `${BASE}/${encodeURIComponent(orgId)}/context`, method: 'get' }),
  settings: (orgId: string) => request({ url: `${BASE}/${encodeURIComponent(orgId)}/settings`, method: 'get' }),
  saveSettings: (orgId: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${encodeURIComponent(orgId)}/settings`, method: 'post', data }),
  deliveries: (orgId: string, params: Record<string, any>) =>
    request({ url: `${BASE}/${encodeURIComponent(orgId)}/deliveries`, method: 'get', params }),
};
