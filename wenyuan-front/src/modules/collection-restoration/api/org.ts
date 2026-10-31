/** 修复机构 API（${apiPath}/collection/restoration-org） */
import request from '@/utils/request';
import type { RestorationOrgVo } from '../typings';

const BASE = '/collection/restoration-org';

export const restorationOrgApi = {
  context(id?: string) {
    return request({ url: `${BASE}/context`, method: 'get', params: { id } });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: RestorationOrgVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: BASE, method: 'post', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  disable(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}/disable`, method: 'post', params: { expectedOrgId, expectedRevision } });
  },
  delete(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },
};
