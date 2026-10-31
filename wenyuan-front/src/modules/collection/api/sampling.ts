import request from '@/utils/request';
const BASE = '/collection/sampling';
export const samplingApi = {
  page: (params: any) => request({ url: `${BASE}/page`, method: 'get', params }),
  create: (data: Record<string, any>) => request({ url: BASE, method: 'post', data }),
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  flow: (id: string) => request({ url: `${BASE}/${id}/flow`, method: 'get' }),
  submit: (id: string) => request({ url: `${BASE}/${id}/submit`, method: 'post' }),
  cancel: (id: string) => request({ url: `${BASE}/${id}/cancel`, method: 'post' }),
  record: (id: string, kind: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/${kind}`, method: 'post', data }),
  sampleRecord: (id: string, sample: string, kind: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/sample/${sample}/${kind}`, method: 'post', data }),
  analysis: (id: string, sample: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/sample/${sample}/analysis`, method: 'post', data }),
};
