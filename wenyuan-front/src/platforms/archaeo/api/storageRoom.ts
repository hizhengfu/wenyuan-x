import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'wms/storage/room/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `wms/storage/room/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'wms/storage/room/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'wms/storage/room/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'wms/storage/room/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'wms/storage/room/update',
    method: 'post',
    data,
  });
}
