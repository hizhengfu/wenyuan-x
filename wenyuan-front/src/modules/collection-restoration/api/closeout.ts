import request from '@/utils/request';
const BASE = '/collection/restoration-closeout';
export const restorationCloseoutApi = {
  detail(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  acceptance(id: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}/acceptance`, method: 'post', data, params: { expectedOrgId, expectedRevision } });
  },
  filing(id: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}/filing`, method: 'post', data, params: { expectedOrgId, expectedRevision } });
  },
};
