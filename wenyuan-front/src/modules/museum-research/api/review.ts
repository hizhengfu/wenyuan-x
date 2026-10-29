/** 专家评审记录 API（${apiPath}/research/review） */
import request from '@/utils/request';

const BASE = '/research/review';

export const reviewApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  summary(reviewType: string, businessId: string) {
    return request({ url: `${BASE}/summary`, method: 'get', params: { reviewType, businessId } });
  },
  invite(data: { reviewType: string; businessId: string; expertIds: string[] }) {
    return request({ url: `${BASE}/invite`, method: 'post', data });
  },
  fill(id: string, data: { score: number; conclusion: string; opinion?: string }) {
    return request({ url: `${BASE}/${id}/fill`, method: 'post', data });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
