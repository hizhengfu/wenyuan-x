import request from '@/utils/request';
const base = (orgId: string) => `/collection/org/${orgId}/leadership-departures`;
export const leadershipDepartureApi = {
  originalUrl(orgId: string, archiveId: string, fileId: string) {
    return `${base(encodeURIComponent(orgId))}/${['original', archiveId, fileId].map(encodeURIComponent).join('/')}`;
  },
  list(orgId: string) {
    return request({ url: base(orgId), method: 'get' });
  },
  register(orgId: string, data: any) {
    return request({ url: base(orgId), method: 'post', data });
  },
  confirm(orgId: string, id: string, stage: string, data: any) {
    return request({ url: `${base(orgId)}/${id}/confirm/${stage}`, method: 'post', data });
  },
};
