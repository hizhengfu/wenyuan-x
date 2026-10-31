/** 藏品研究数据开放获取 API（${apiPath}/research/data-access） */
import request from '@/utils/request';
import type { ResDataAccessVo } from '../typings';

const BASE = '/research/data-access';

export const dataAccessApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: ResDataAccessVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  approve(id: string, pass: boolean, opinion?: string, validFrom?: string, validTo?: string) {
    return request({ url: `${BASE}/${id}/approve`, method: 'post', params: { pass, opinion, validFrom, validTo } });
  },
  revoke(id: string, opinion?: string) {
    return request({ url: `${BASE}/${id}/revoke`, method: 'post', params: { opinion } });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
