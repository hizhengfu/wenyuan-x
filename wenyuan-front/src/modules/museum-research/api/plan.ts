/** 课题实施计划 API（${apiPath}/research/topic-plan） */
import request from '@/utils/request';
import type { ResTopicPlanVo } from '../typings';

const BASE = '/research/topic-plan';

export const planApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  byTopic(topicId: string) {
    return request({ url: `${BASE}/by-topic/${topicId}`, method: 'get' });
  },
  save(vo: ResTopicPlanVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
