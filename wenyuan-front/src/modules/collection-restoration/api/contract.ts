/** 修复合同 API（${apiPath}/collection/restoration-contract） */
import request from '@/utils/request';
import type { RestorationContractVo } from '../typings';

const BASE = '/collection/restoration-contract';

export const restorationContractApi = {
  context(restorationId: string) {
    return request({ url: `${BASE}/context/${restorationId}`, method: 'get' });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  getById(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  getByRestoration(restorationId: string) {
    return request({ url: `${BASE}/by-restoration/${restorationId}`, method: 'get' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
  save(vo: RestorationContractVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: BASE, method: 'post', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  deleteById(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },
  deleteByRestoration(restorationId: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/by-restoration/${restorationId}`, method: 'delete', params: { expectedOrgId, expectedRevision } });
  },
};
