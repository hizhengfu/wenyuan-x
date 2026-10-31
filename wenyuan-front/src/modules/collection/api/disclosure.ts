import request from '@/utils/request';
const base = '/collection/disclosure';
export const disclosureApi = {
  detail: (org: string, subject: string, id: string) =>
    request({ url: `${base}/${org}/${subject}/${id}`, method: 'get' }),
  review: (org: string, subject: string, id: string, data: any) =>
    request({ url: `${base}/${org}/${subject}/${id}`, method: 'post', data }),
  enable: (org: string, id: string) => request({ url: `${base}/${org}/review/${id}/enable`, method: 'post' }),
  stop: (org: string, id: string, data: any) =>
    request({ url: `${base}/${org}/review/${id}/stop`, method: 'post', data }),
  event: (org: string, id: string, data: any) =>
    request({ url: `${base}/${org}/review/${id}/event`, method: 'post', data }),
};
