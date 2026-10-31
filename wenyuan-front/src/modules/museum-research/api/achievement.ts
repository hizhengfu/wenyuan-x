/** 研究成果 API（${apiPath}/research/achievement） */
import request from '@/utils/request';
import type { ResAchievementVo } from '../typings';

const BASE = '/research/achievement';

export const achievementApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  showcase(params: any) {
    return request({ url: `${BASE}/showcase`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: ResAchievementVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
  publish(id: string, published: boolean) {
    return request({ url: `${BASE}/${id}/publish`, method: 'post', params: { published } });
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
