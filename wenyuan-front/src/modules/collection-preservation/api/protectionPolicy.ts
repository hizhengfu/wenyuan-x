import request from '@/utils/request';
import type { ProtectionPolicyStatus, ProtectionPolicyVo } from '../typings';

const BASE = '/preservation/protection-policies';

export const protectionPolicyApi = {
  list: () => request({ url: BASE, method: 'get' }),
  adopt: (data: {
    templateId: string;
    code?: string;
    name?: string;
    templateVersionId: string;
    expectedOrgId: string;
  }) => request({ url: `${BASE}/adopt`, method: 'post', data }),
  saveAndActivate: (id: string, vo: ProtectionPolicyVo) =>
    request({ url: `${BASE}/${id}/activate`, method: 'put', data: vo }),
  saveDraft: (id: string, vo: ProtectionPolicyVo) => request({ url: `${BASE}/${id}/draft`, method: 'put', data: vo }),
  publish: (id: string, data: { policyVersionId: string; definitionHash: string; assessmentId: string }) =>
    request({ url: `${BASE}/${id}/publish`, method: 'post', data }),
  assessment: (id: string) => request({ url: `${BASE}/${id}/assessment`, method: 'get' }),
  registerAssessment: (id: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/assessment`, method: 'post', data }),
  signAssessment: (id: string, assessmentId: string, participantNumber: number, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/assessment/${assessmentId}/sign/${participantNumber}`, method: 'post', data }),
  setStatus: (id: string, status: ProtectionPolicyStatus) =>
    request({ url: `${BASE}/${id}/status`, method: 'post', data: { status } }),
  batchStatus: (ids: string[], status: ProtectionPolicyStatus) =>
    request({ url: `${BASE}/batch-status`, method: 'post', data: { ids, status } }),
};
