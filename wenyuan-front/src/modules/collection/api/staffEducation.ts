import request from '@/utils/request';
const BASE = '/collection/staff-education';
export const staffEducationApi = {
  context: () => request({ url: `${BASE}/context`, method: 'get' }),
  people: (orgId: string) => request({ url: `${BASE}/people`, method: 'get', params: { orgId } }),
  list: (params: any) => request({ url: BASE, method: 'get', params }),
  detail: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  create: (orgId: string, data: any) => request({ url: BASE, method: 'post', params: { orgId }, data }),
  revise: (id: string, data: any) => request({ url: `${BASE}/${id}/revision`, method: 'post', data }),
  confirm: (id: string, version: number, data: any) =>
    request({ url: `${BASE}/${id}/versions/${version}/confirmation`, method: 'post', data }),
};
