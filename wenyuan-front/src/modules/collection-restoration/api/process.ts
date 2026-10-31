/** 修复工序 API（${apiPath}/collection/restoration-process） */
import request from '@/utils/request';
import type { RestorationProcessVo } from '../typings';

const BASE = '/collection/restoration-process';

export const restorationProcessApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  getById(id: string) {
    return request({ url: `${BASE}/id/${id}`, method: 'get' });
  },
  list(restorationId: string) {
    return request({ url: `${BASE}/list/${restorationId}`, method: 'get' });
  },
  context(restorationId: string) {
    return request({ url: `${BASE}/context/${restorationId}`, method: 'get' });
  },
  save(vo: RestorationProcessVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: BASE, method: 'post', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  reorder(restorationId: string, orderedIds: string[], expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${restorationId}/reorder`, method: 'post', data: orderedIds, params: { expectedOrgId, expectedRevision } });
  },
  delete(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },
  summarize(restorationId: string) {
    return request({ url: `${BASE}/summarize/${restorationId}`, method: 'get' });
  },
};
