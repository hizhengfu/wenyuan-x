/** 提用单（出入库流程）API（${apiPath}/collection/requisition） */
import request from '@/utils/request';
import type { CollectionOutboundExecutionVo, RequisitionVo } from '../typings/collection';

const BASE = '/collection/requisition';

export const requisitionApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string, signal?: AbortSignal) {
    return request({ url: `${BASE}/${id}`, method: 'get', signal });
  },
  replacementInfo(id: string) {
    return request({ url: `${BASE}/${id}/responsibility-replacement`, method: 'get' });
  },
  replaceResponsibility(id: string, data: Record<string, any>) {
    return request({ url: `${BASE}/${id}/responsibility-replacement`, method: 'post', data });
  },
  save(vo: RequisitionVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  handover(id: string, data: Record<string, any>) {
    return request({ url: `${BASE}/${id}/handover`, method: 'post', data });
  },
  executeOut(id: string, data: CollectionOutboundExecutionVo) {
    return request({ url: `${BASE}/${id}/execute-out`, method: 'post', data });
  },
  /** 归还入库（审批通过/出库后） */
  returnBack(id: string) {
    return request({ url: `${BASE}/${id}/return`, method: 'post' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
};
