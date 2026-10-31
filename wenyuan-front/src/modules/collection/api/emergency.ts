import request from '@/utils/request';
const BASE = '/collection/emergency';
export const emergencyApi = {
  page: (params: Record<string, any>) => request({ url: `${BASE}/page`, method: 'get', params }),
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  originalUrl: (orgId: string, recordId: string, phase: string, fileId: string) =>
    `${BASE}/${[orgId, 'original', recordId, phase, fileId].map(encodeURIComponent).join('/')}`,
  record: (id: string, path: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/${path}`, method: 'post', data }),
};
