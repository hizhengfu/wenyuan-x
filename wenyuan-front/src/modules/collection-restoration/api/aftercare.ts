import request from '@/utils/request';
const BASE = '/collection/restoration-aftercare';
export const restorationAftercareApi = {
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  check: (id: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) => request({ url: `${BASE}/${id}/check`, method: 'post', data, params: { expectedOrgId, expectedRevision } }),
  review: (id: string, check: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) =>
    request({ url: `${BASE}/${id}/check/${check}/review`, method: 'post', data, params: { expectedOrgId, expectedRevision } }),
};
