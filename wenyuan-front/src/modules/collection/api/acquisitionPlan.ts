/** 征集计划 API（后端 ${apiPath}/collection/acquisition-plan，流程驱动） */
import request from '@/utils/request';
import type { AcquisitionPlanVo } from '../typings/collection';

const BASE = '/collection/acquisition-plan';

export const acquisitionPlanApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: AcquisitionPlanVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  archiveOfflineApproval(id: string, data: import('../typings/collection').AcquisitionPlanOfflineApproval) {
    return request({ url: `${BASE}/${id}/offline-approval`, method: 'post', data });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  void(id: string) {
    return request({ url: `${BASE}/${id}/void`, method: 'post' });
  },
  flowInfo(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
};
