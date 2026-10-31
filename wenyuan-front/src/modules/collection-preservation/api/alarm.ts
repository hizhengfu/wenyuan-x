/**
 * 预警事件与处置 API
 * 后端路径：${apiPath}/preservation/alarm
 */
import request from '@/utils/request';

const BASE = '/preservation/alarm';

export const alarmApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
  affected(id: string) {
    return request({ url: `${BASE}/${id}/affected-collections`, method: 'get' });
  },
  start(id: string) {
    return request({ url: `${BASE}/${id}/start`, method: 'post' });
  },
  process(id: string, reason: string) {
    return request({ url: `${BASE}/${id}/process`, method: 'post', data: { reason } });
  },
  close(id: string, data: Record<string, any>, expectedOrgId:string, expectedRevision:string) {
    return request({ url: `${BASE}/${id}/close`, method: 'post', data, params:{expectedOrgId,expectedRevision} });
  },
  closureContext(id:string) { return request({url:`${BASE}/${id}/closure-context`,method:'get'}); },
  closureRecords(id: string) { return request({url:`${BASE}/${id}/closure-records`,method:'get'}); },
  dashboard() {
    return request({ url: `${BASE}/dashboard`, method: 'get' });
  },
};
