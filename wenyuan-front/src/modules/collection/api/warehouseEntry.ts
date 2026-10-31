import request from '@/utils/request';
import type { WarehouseEntryApplicationVo } from '../typings/collection';

const BASE = '/collection/warehouse-entry';

export const warehouseEntryApi = {
  timeline(id: string) {
    return request({ url: `${BASE}/${id}/timeline`, method: 'get' });
  },
  reviewTimeline(id: string, data: Record<string, any>) {
    return request({ url: `${BASE}/${id}/timeline/review`, method: 'post', data });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  applicationContext(id?: string) {
    return request({ url: id ? `${BASE}/${id}/application-context` : `${BASE}/application-context`, method: 'get' });
  },
  save(vo: WarehouseEntryApplicationVo, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo, params: { expectedOrgId, expectedRevision } });
  },
  submit(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post', params: { expectedOrgId, expectedRevision } });
  },
  cancel(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post', params: { expectedOrgId, expectedRevision } });
  },
  void(id: string, expectedOrgId: string, expectedRevision: string) {
    return request({ url: `${BASE}/${id}/void`, method: 'post', params: { expectedOrgId, expectedRevision } });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
  attendanceContext(id: string) {
    return request({ url: `${BASE}/${id}/attendance-context`, method: 'get' });
  },
  checkIn(id: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) {
    return request({
      url: `${BASE}/${id}/check-in`,
      method: 'post',
      data,
      params: { expectedOrgId, expectedRevision },
    });
  },
  checkOut(id: string, data: Record<string, any>, expectedOrgId: string, expectedRevision: string) {
    return request({
      url: `${BASE}/${id}/check-out`,
      method: 'post',
      data,
      params: { expectedOrgId, expectedRevision },
    });
  },
};
