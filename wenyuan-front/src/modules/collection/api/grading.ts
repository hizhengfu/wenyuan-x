import request from '@/utils/request';
const BASE = '/collection/grading';
export const gradingApi = {
  detail(id: string) {
    return request({ url: `${BASE}/collection/${id}`, method: 'get' });
  },
  historyPage(keyword?: string) {
    return request({ url: `${BASE}/history/page`, method: 'get', params: { keyword, pageSize: 50 } });
  },
  history(id: string) {
    return request({ url: `${BASE}/history/${encodeURIComponent(id)}`, method: 'get' });
  },
  application(id: string, data: any) {
    return request({ url: `${BASE}/collection/${id}/application`, method: 'post', data });
  },
  result(id: string, data: any) {
    return request({ url: `${BASE}/${id}/result`, method: 'post', data });
  },
  national(id: string, data: any) {
    return request({ url: `${BASE}/${id}/national`, method: 'post', data });
  },
};
