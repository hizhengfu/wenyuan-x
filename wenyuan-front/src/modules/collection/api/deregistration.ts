/** 退出馆藏申请 API（流程驱动，${apiPath}/collection/deregistration） */
import request from '@/utils/request';
import type { DeregistrationVo } from '../typings/collection';

const BASE = '/collection/deregistration';

export const deregistrationApi = {
  archive: (id: string, signal?: AbortSignal) => request({ url: `${BASE}/${id}/archive`, method: 'get', signal }),
  publicReviews: (id: string) => request({ url: `${BASE}/${id}/public-reviews`, method: 'get' }),
  expert: (id: string, data: Record<string, any>) => request({ url: `${BASE}/${id}/expert`, method: 'post', data }),
  notice: (id: string, data: Record<string, any>) => request({ url: `${BASE}/${id}/notice`, method: 'post', data }),
  administrative: (id: string, data: Record<string, any>) =>
    request({ url: `${BASE}/${id}/administrative`, method: 'post', data }),
  execute: (id: string, data: Record<string, any>) => request({ url: `${BASE}/${id}/execute`, method: 'post', data }),
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
  save(vo: DeregistrationVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  /** 作废：后端对 PASS 拒绝；前端须在 PASS 禁用（见 canVoidDeregistration）。 */
  voidRecord(id: string) {
    return request({ url: `${BASE}/${id}/void`, method: 'post' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
};
