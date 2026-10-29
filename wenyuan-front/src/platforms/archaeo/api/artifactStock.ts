import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'wms/stock/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `wms/stock/${id}`,
    method: 'get',
  });
}
