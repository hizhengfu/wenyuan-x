import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'wms/storage/location/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `wms/storage/location/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'wms/storage/location/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'wms/storage/location/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'wms/storage/location/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'wms/storage/location/update',
    method: 'post',
    data,
  });
}
