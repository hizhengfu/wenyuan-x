import request from '@/utils/request';
const BASE = '/collection/designated-reception';
export const designatedReceptionApi = {
  context: () => request({ url: `${BASE}/context`, method: 'get' }),
  page: (page: number, keyword = '') =>
    request({ url: `${BASE}/page`, method: 'get', params: { page, pageSize: 20, keyword } }),
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  create: (data: Record<string, any>, expectedOrgId: string) =>
    request({ url: BASE, method: 'post', data, params: { expectedOrgId } }),
  record: (id: string, phase: string, data: Record<string, any>, expectedOrgId: string) =>
    request({ url: `${BASE}/${id}/${phase}`, method: 'post', data, params: { expectedOrgId } }),
};
