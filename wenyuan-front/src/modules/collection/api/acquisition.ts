/** 征集入藏 API（流程驱动，${apiPath}/collection/acquisition） */
import request from '@/utils/request';
import type { AcquisitionImportRowVo, AcquisitionVo } from '../typings/collection';

const BASE = '/collection/acquisition';

export const acquisitionApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: AcquisitionVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  importTemplate() {
    return request({ url: `${BASE}/import/template`, method: 'get' });
  },
  validateImport(rows: AcquisitionImportRowVo[]) {
    return request({ url: `${BASE}/import/validate`, method: 'post', data: rows });
  },
  commitImport(rows: AcquisitionImportRowVo[]) {
    return request({ url: `${BASE}/import/commit`, method: 'post', data: rows });
  },
  /** 提交审批（发起流程） */
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  /** 撤销 */
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  /** 流程进度（图 + 历史） */
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
};
