import request from '@/utils/request';
const BASE = '/collection/care';
export const careApi = {
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  history: (id: string, orgId: string) => request({ url: `${BASE}/${id}/history`, method: 'get', params: { orgId } }),
  originalUrl: (id: string, orgId: string, recordId: string, phase: string, fileId: string) =>
    `${BASE}/${[id, 'original', orgId, recordId, phase, fileId].map(encodeURIComponent).join('/')}`,
  record: (id: string, path: string, data: Record<string, any>, sourceRevision?: string) =>
    request({ url: `${BASE}/${id}/${path}`, method: 'post', data, params: sourceRevision === undefined ? undefined : { sourceRevision } }),
};
