import request from '@/utils/request';
import type { RuleTemplateVo } from '../typings';

const BASE = '/preservation/rule-templates';

export const ruleTemplateApi = {
  list: () => request({ url: BASE, method: 'get' }),
  context: () => request({ url: `${BASE}/context`, method: 'get' }),
  get: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  save: (vo: RuleTemplateVo) => request({ url: BASE, method: 'post', data: vo }),
  publish: (id: string, data: { versionId: string; definitionHash: string }) =>
    request({ url: `${BASE}/${id}/publish`, method: 'post', data }),
  retire: (id: string) => request({ url: `${BASE}/${id}/retire`, method: 'post' }),
};
