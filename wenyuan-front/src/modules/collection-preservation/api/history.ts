/**
 * 历史数据查询 API
 * 后端路径：${apiPath}/preservation/history
 */
import request from '@/utils/request';

const BASE = '/preservation/history';

export const historyApi = {
  get(id: string) {
    return request({ url: `${BASE}/record/${encodeURIComponent(id)}`, method: 'get' });
  },
  latestCards() {
    return request({ url: `${BASE}/latest-cards`, method: 'get' });
  },
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  series(params: { sensorPointId: string; sensorId: string; from: number; to: number; limit?: number }) {
    return request({ url: `${BASE}/series`, method: 'get', params });
  },
  locationSeries(params: {
    warehouseId: string;
    deviceId?: string;
    sensorId?: string;
    valueOperator?: 'GT' | 'LT' | 'BETWEEN';
    value?: number;
    value2?: number;
    from: number;
    to: number;
    limit?: number;
  }) {
    return request({ url: `${BASE}/location-series`, method: 'get', params });
  },
  latest(params: { sensorPointId: string; sensorId: string }) {
    return request({ url: `${BASE}/latest`, method: 'get', params });
  },
};
