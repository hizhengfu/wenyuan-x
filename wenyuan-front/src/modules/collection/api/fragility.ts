import request from '@/utils/request';
const BASE = '/collection/fragility';
export const fragilityApi = {
  detail: (id: string, options: { silentError?: boolean } = {}) =>
    request({ url: `${BASE}/${id}`, method: 'get', silentError: options.silentError }),
  originalUrl: (id: string, orgId: string, recordId: string, phase: string, fileId: string) =>
    `${BASE}/${[id, 'original', orgId, recordId, phase, fileId].map(encodeURIComponent).join('/')}`,
  loanMonitoringOriginalUrl: (id: string, orgId: string, adjustmentId: string, monitoringId: string, fileId: string) =>
    `${BASE}/${[id, 'original', 'loan-monitoring', orgId, adjustmentId, monitoringId, fileId].map(encodeURIComponent).join('/')}`,
  samples: (id: string, recordId: string, page: number) =>
    request({ url: `${BASE}/${id}/monitor/${recordId}/samples`, method: 'get', params: { page, pageSize: 20 } }),
  automaticStatus: (id: string) => request({ url: `${BASE}/${id}/automatic-status`, method: 'get' }),
  automaticChecks: (id: string, useId: string, page: number) =>
    request({ url: `${BASE}/${id}/record/${useId}/automatic`, method: 'get', params: { page, pageSize: 20 } }),
  automaticSamples: (id: string, recordId: string, page: number) =>
    request({ url: `${BASE}/${id}/automatic/${recordId}/samples`, method: 'get', params: { page, pageSize: 20 } }),
  environmentSources: (id: string) => request({ url: `${BASE}/${id}/environment-sources`, method: 'get' }),
  source: (id: string, sourceBizType: string, sourceBizId: string) =>
    request({ url: `${BASE}/${id}/source`, method: 'get', params: { sourceBizType, sourceBizId } }),
  record: (id: string, path: string, data: Record<string, any>, expectedOrgId: string) =>
    request({ url: `${BASE}/${id}/${path}`, method: 'post', data, params: { expectedOrgId } }),
};
