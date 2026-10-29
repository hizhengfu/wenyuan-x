import request from '@/utils/request';
const base = (org: string) => `/collection/org/${org}/archives`;
export const archivePacketsApi = {
  collections: (org: string) => request({ url: `${base(org)}/collections`, method: 'get' }),
  jurisdictions: (org: string) => request({ url: `${base(org)}/jurisdiction`, method: 'get' }),
  jurisdiction: (org: string, data: any) => request({ url: `${base(org)}/jurisdiction`, method: 'post', data }),
  detail: (org: string, id: string) => request({ url: `${base(org)}/collection/${id}`, method: 'get' }),
  record: (org: string, id: string, data: any) =>
    request({ url: `${base(org)}/collection/${id}`, method: 'post', data }),
  submission: (org: string, id: string, data: any) =>
    request({ url: `${base(org)}/packet/${id}/submission`, method: 'post', data }),
  receipt: (org: string, id: string, data: any) =>
    request({ url: `${base(org)}/submission/${id}/receipt`, method: 'post', data }),
};
