import request from '@/utils/request';
const base = (id: string) => `/collection/restoration/${id}/restricted`;
export const restorationRestrictedApi = {
  detail: (id: string) => request({ url: base(id), method: 'get' }),
  create: (id: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) => request({ url: base(id), method: 'post', data, params: { expectedOrgId, expectedRevision } }),
  confirm: (id: string, authorizationId: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) =>
    request({ url: `${base(id)}/${authorizationId}/confirm`, method: 'post', data, params: { expectedOrgId, expectedRevision } }),
  cancel: (id: string, authorizationId: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) =>
    request({ url: `${base(id)}/${authorizationId}/cancel`, method: 'post', data, params: { expectedOrgId, expectedRevision } }),
};
