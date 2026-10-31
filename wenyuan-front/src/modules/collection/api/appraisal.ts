/** 鉴定 API（${apiPath}/collection/appraisal） */
import request from '@/utils/request';
import type { AppraisalVo } from '../typings/collection';

const BASE = '/collection/appraisal';

export const appraisalApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  apply(vo: AppraisalVo) {
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
  createChange(id: string) {
    return request({ url: `${BASE}/${id}/change`, method: 'post' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
};
