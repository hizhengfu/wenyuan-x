/** 修复人员 API（${apiPath}/collection/restorer） */
import request from '@/utils/request';
import type { RestorerVo } from '../typings';

const BASE = '/collection/restorer';

export const restorerApi = {
  context(id?: string) {
    return request({ url: `${BASE}/context`, method: 'get', params: { id } });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  bySpeciality(speciality: string) {
    return request({ url: `${BASE}/by-speciality`, method: 'get', params: { speciality } });
  },
  save(vo: RestorerVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: BASE, method: 'post', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  disable(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}/disable`, method: 'post', params: { expectedOrgId, expectedRevision } });
  },
  delete(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },
};
