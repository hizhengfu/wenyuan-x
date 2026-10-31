/** 跨馆调拨 API（流程驱动，${apiPath}/collection/transfer） */
import request from '@/utils/request';
import type { CollectionTransferVo } from '../typings/collection';

const BASE = '/collection/transfer';

export const transferApi = {
  archive: (id: string, signal?: AbortSignal) => request({ url: `${BASE}/${id}/archive`, method: 'get', signal }),
  publicReviews: (id: string) => request({ url: `${BASE}/${id}/public-reviews`, method: 'get' }),
  parties() {
    return request({ url: `${BASE}/parties`, method: 'get' });
  },
  receivingLocations(id: string) {
    return request({ url: `${BASE}/${id}/receiving-locations`, method: 'get' });
  },
  administrative(id: string, data: Record<string, any>) {
    return request({ url: `${BASE}/${id}/administrative`, method: 'post', data });
  },
  execute(id: string, data: Record<string, any>) {
    return request({ url: `${BASE}/${id}/execute`, method: 'post', data });
  },
  notice(id: string, data: Record<string, any>) {
    return request({ url: `${BASE}/${id}/notice`, method: 'post', data });
  },
  termination(id: string) {
    return request({ url: `${BASE}/${id}/termination`, method: 'get' });
  },
  proposeTermination(id: string, data: Record<string, any>) {
    return request({ url: `${BASE}/${id}/termination/propose`, method: 'post', data });
  },
  confirmTermination(id: string, data: Record<string, any>) {
    return request({ url: `${BASE}/${id}/termination/confirm`, method: 'post', data });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  save(vo: CollectionTransferVo) {
    return request({ url: `${BASE}/save`, method: 'post', data: vo });
  },
  submit(id: string) {
    return request({ url: `${BASE}/${id}/submit`, method: 'post' });
  },
  cancel(id: string) {
    return request({ url: `${BASE}/${id}/cancel`, method: 'post' });
  },
  flow(id: string) {
    return request({ url: `${BASE}/${id}/flow`, method: 'get' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
};
