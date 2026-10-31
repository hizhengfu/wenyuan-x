/** 研究课题 API（${apiPath}/research/topic） */
import request from '@/utils/request';
import type { ResTopicVo } from '../typings';

const BASE = '/research/topic';

export const topicApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: ResTopicVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
  terminate(id: string, reason?: string) {
    return request({ url: `${BASE}/${id}/terminate`, method: 'post', params: { reason } });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  void(id: string) {
    return request({ url: `${BASE}/${id}/void`, method: 'post' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
};
