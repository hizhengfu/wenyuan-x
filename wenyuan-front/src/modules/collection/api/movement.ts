/** 出入库流水 API（后端 ${apiPath}/collection/movement） */
import request from '@/utils/request';

const BASE = '/collection/movement';

export const movementApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  byCollection(collectionId: string) {
    return request({ url: `${BASE}/collection/${collectionId}`, method: 'get' });
  },
  print(id: string) {
    return request({ url: `${BASE}/${id}/print`, method: 'get' });
  },
};
