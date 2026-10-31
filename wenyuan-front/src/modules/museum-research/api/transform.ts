/** 成果转化 API（${apiPath}/research/transform） */
import request from '@/utils/request';
import type { ResTransformVo } from '../typings';

const BASE = '/research/transform';

export const transformApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: ResTransformVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  approve(id: string, pass: boolean, opinion?: string) {
    return request({ url: `${BASE}/${id}/approve`, method: 'post', params: { pass, opinion } });
  },
  track(id: string, progressNote: string) {
    return request({ url: `${BASE}/${id}/track`, method: 'post', params: { progressNote } });
  },
  complete(id: string, effect?: string, report?: string) {
    return request({ url: `${BASE}/${id}/complete`, method: 'post', params: { effect, report } });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
