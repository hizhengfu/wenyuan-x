import request from '@/utils/request';
const BASE = '/collection/joint-safety';
export const jointSafetyApi = {
  page: (params: Record<string, any>) => request({ url: `${BASE}/page`, method: 'get', params }),
  detail: (id: string, options: { silentError?: boolean } = {}) =>
    request({ url: `${BASE}/${id}`, method: 'get', silentError: options.silentError }),
  record: (id: string, path: string, data: Record<string, any>, expectedOrgId: string) =>
    request({ url: `${BASE}/${id}/${path}`, method: 'post', data, params: { expectedOrgId } }),
};
