/**
 * 统一位置只读目录 API
 * 后端路径：${apiPath}/preservation/point
 */
import request from '@/utils/request';

const BASE = '/preservation/point';

export const sensorPointApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  byWarehouse(warehouseId: string) {
    return request({ url: `${BASE}/by-warehouse/${warehouseId}`, method: 'get' });
  },
  get(id: string) {
    return request({ url: `${BASE}/${id}`, method: 'get' });
  },
};
