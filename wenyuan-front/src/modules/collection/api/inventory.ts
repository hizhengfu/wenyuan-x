/** 盘点 API（${apiPath}/collection/inventory） */
import request from '@/utils/request';
import type { InventoryBatchVo, InventoryImportRowVo, InventoryVo } from '../typings/collection';

const BASE = '/collection/inventory';

export const inventoryApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  batches(params: any) {
    return request({ url: `${BASE}/batches`, method: 'get', params });
  },
  batchConfigs(options: { silentError?: boolean } = {}) {
    return request({ url: `${BASE}/batch`, method: 'get', silentError: options.silentError });
  },
  saveBatch(vo: InventoryBatchVo) {
    return request({ url: `${BASE}/batch`, method: 'post', data: vo });
  },
  batch(id: string) { return request({url:`${BASE}/batch/${id}`,method:'get'}); },
  compliance(id: string, action: string, data: any) { return request({url:`${BASE}/batch/${id}/${action}`,method:'post',data}); },
  coverage(year: number) { return request({url:`${BASE}/annual/${year}/coverage`,method:'get'}); },
  annualReports(year: number) { return request({url:`${BASE}/annual/${year}/reports`,method:'get'}); },
  annualReport(data: any) { return request({url:`${BASE}/annual/report`,method:'post',data}); },
  create(vo: InventoryVo) {
    return request({ url: BASE, method: 'post', data: vo });
  },
  save(vo: InventoryVo) {
    return request({ url: BASE, method: 'post', data: vo });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
  importTemplate() {
    return request({ url: `${BASE}/import/template`, method: 'get' });
  },
  validateImport(rows: InventoryImportRowVo[]) {
    return request({ url: `${BASE}/import/validate`, method: 'post', data: rows });
  },
  commitImport(rows: InventoryImportRowVo[]) {
    return request({ url: `${BASE}/import/commit`, method: 'post', data: rows });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
  /** RFID 批量盘点：扫描标签自动比对生成盘点草稿行 */
  rfidCheck(data: { batchName: string; warehouseId?: string; tags: string[] }) {
    return request({ url: `${BASE}/rfid-check`, method: 'post', data });
  },
};
