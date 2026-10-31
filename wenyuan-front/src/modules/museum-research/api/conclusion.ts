/** 课题结项 API（${apiPath}/research/topic-conclusion） */
import request from '@/utils/request';
import type { ResTopicConclusionVo } from '../typings';

const BASE = '/research/topic-conclusion';

export const conclusionApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  byTopic(topicId: string) {
    return request({ url: `${BASE}/by-topic/${topicId}`, method: 'get' });
  },
  save(vo: ResTopicConclusionVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  audit(id: string, pass: boolean, opinion?: string) {
    return request({ url: `${BASE}/${id}/audit`, method: 'post', params: { pass, opinion } });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
