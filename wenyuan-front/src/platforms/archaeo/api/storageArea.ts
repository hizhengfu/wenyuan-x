import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'wms/storage/area/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `wms/storage/area/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'wms/storage/area/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'wms/storage/area/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'wms/storage/area/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'wms/storage/area/update',
    method: 'post',
    data,
  });
}
