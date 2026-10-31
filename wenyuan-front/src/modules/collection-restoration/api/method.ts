/** 修复方法 API（${apiPath}/collection/restoration-method） */
import request from '@/utils/request';
import type { RestorationMethodVo } from '../typings';

const BASE = '/collection/restoration-method';

export const restorationMethodApi = {
  context(id?: string) {
    return request({ url: `${BASE}/context`, method: 'get', params: { id } });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  list(params?: any) {
    return request({ url: BASE, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  create(vo: RestorationMethodVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: BASE, method: 'post', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  update(id: string, vo: RestorationMethodVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}`, method: 'put', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  delete(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },
};
