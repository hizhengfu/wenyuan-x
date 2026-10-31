/** 藏品展览 API（${apiPath}/collection/exhibition） */
import request from '@/utils/request';
import type { ExhibitionVo } from '../typings/collection';

const BASE = '/collection/exhibition';

export const exhibitionApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
  create(vo: ExhibitionVo) {
    return request({ url: BASE, method: 'post', data: vo });
  },
  update(id: string, vo: ExhibitionVo) {
    return request({ url: `${BASE}/${id}`, method: 'put', data: vo });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  voidRecord(id: string) {
    return request({ url: `${BASE}/${id}/void`, method: 'post' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
