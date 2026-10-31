/** 成果评价 API（${apiPath}/research/achievement-eval） */
import request from '@/utils/request';
import type { ResAchievementEvalVo } from '../typings';

const BASE = '/research/achievement-eval';

export const evaluationApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  byAchievement(achievementId: string) {
    return request({ url: `${BASE}/by-achievement/${achievementId}`, method: 'get' });
  },
  history(achievementId: string, orgId: string) {
    return request({ url: `${BASE}/history/${encodeURIComponent(achievementId)}`, method: 'get', params: { orgId } });
  },
  save(vo: ResAchievementEvalVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  delete(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'delete' });
  },
};
