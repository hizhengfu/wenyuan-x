/** 库房库位 API（后端 ${apiPath}/collection/warehouse） */
import request from '@/utils/request';
import type { WarehouseImportRowVo, WarehouseVo } from '../typings/collection';

const BASE = '/collection/warehouse';

export const warehouseApi = {
  staffingHandovers(id: string) {
    return request({ url: `${BASE}/${id}/staffing-handovers`, method: 'get' });
  },
  staffingHandoverOriginalUrl(id: string, archiveId: string, fileId: string) {
    return `${BASE}/${[id, 'staffing-handovers', 'original', archiveId, fileId].map(encodeURIComponent).join('/')}`;
  },
  staffingHandoverAssignmentOriginalUrl(id: string, archiveId: string, fileId: string) {
    return `${BASE}/${[id, 'staffing-handovers', 'original', 'assignment', archiveId, fileId].map(encodeURIComponent).join('/')}`;
  },
  registerStaffingHandover(id: string, data: any) {
    return request({ url: `${BASE}/${id}/staffing-handovers`, method: 'post', data });
  },
  confirmStaffingHandover(id: string, recordId: string, role: string, data: any) {
    return request({ url: `${BASE}/${id}/staffing-handovers/${recordId}/confirm/${role}`, method: 'post', data });
  },
  staffing(id: string) {
    return request({ url: `${BASE}/${id}/staffing`, method: 'get' });
  },
  saveStaffing(id: string, data: any) {
    return request({ url: `${BASE}/${id}/staffing`, method: 'post', data });
  },
  tree() {
    return request({ url: `${BASE}/tree`, method: 'get' });
  },
  list() {
    return request({ url: BASE, method: 'get' });
  },
  locationCheck() {
    return request({ url: `${BASE}/location-check`, method: 'get' });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  create(vo: WarehouseVo) {
    return request({ url: BASE, method: 'post', data: vo });
  },
  importTemplate() {
    return request({ url: `${BASE}/import/template`, method: 'get' });
  },
  validateImport(rows: WarehouseImportRowVo[]) {
    return request({ url: `${BASE}/import/validate`, method: 'post', data: rows });
  },
  commitImport(rows: WarehouseImportRowVo[]) {
    return request({ url: `${BASE}/import/commit`, method: 'post', data: rows });
  },
  update(id: string, vo: WarehouseVo) {
    return request({ url: `${BASE}/${id}`, method: 'put', data: vo });
  },
  retireAsExhibition(id: string, data: { replacement: WarehouseVo; reason: string; signedFileIds: string }) {
    return request({ url: `${BASE}/${id}/retire-as-exhibition`, method: 'post', data });
  },
  corrections(id: string) {
    return request({ url: `${BASE}/${id}/corrections`, method: 'get' });
  },
  correctionOriginalUrl(id: string, archiveId: string, fileId: string) {
    return `${BASE}/${[id, 'corrections', 'original', archiveId, fileId].map(encodeURIComponent).join('/')}`;
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
