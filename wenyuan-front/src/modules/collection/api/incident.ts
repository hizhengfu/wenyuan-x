import request from '@/utils/request';
const BASE = '/collection/incident';
export const incidentApi = {
  page: (params: any) => request({ url: `${BASE}/page`, method: 'get', params }),
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  create: (data: Record<string, any>) => request({ url: BASE, method: 'post', data }),
  record: (id: string, kind: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/${kind}`, method: 'post', data }),
};
