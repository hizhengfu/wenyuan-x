import request from '@/utils/request';
const BASE = '/collection/institution-succession';
export const institutionSuccessionApi = {
  context: () => request({ url: `${BASE}/context`, method: 'get' }),
  page: (page: number) => request({ url: `${BASE}/page`, method: 'get', params: { page, pageSize: 20 } }),
  detail: (id: string, page = 1) => request({ url: `${BASE}/${id}`, method: 'get', params: { page, pageSize: 20 } }),
  create: (data: Record<string, any>, expectedOrgId: string) =>
    request({ url: BASE, method: 'post', data, params: { expectedOrgId } }),
  record: (id: string, phase: string, data: Record<string, any>, expectedOrgId: string) =>
    request({ url: `${BASE}/${id}/${phase}`, method: 'post', data, params: { expectedOrgId } }),
};
