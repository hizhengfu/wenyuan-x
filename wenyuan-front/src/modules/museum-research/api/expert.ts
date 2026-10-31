/** 专家库 API（${apiPath}/research/expert） */
import request from '@/utils/request';
import type { ResExpertVo } from '../typings';

const BASE = '/research/expert';

export const expertApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  options(field?: string) {
    return request({ url: `${BASE}/options`, method: 'get', params: { field } });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: ResExpertVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  setEnabled(id: string, enabled: boolean) {
    return request({ url: `${BASE}/${id}/enabled`, method: 'post', params: { enabled } });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
