/** 复制/拓印 API（${apiPath}/collection/reproduction） */
import request from '@/utils/request';
import type { ReproductionVo } from '../typings/collection';

const BASE = '/collection/reproduction';

export const reproductionApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  create(vo: ReproductionVo) {
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
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
};
