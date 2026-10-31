import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'wms/project/plot/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `wms/project/plot/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'wms/project/plot/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'wms/project/plot/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'wms/project/plot/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'wms/project/plot/update',
    method: 'post',
    data,
  });
}

export function tree(params: object) {
  return request({
    url: 'wms/project/plot/tree',
    params: params,
    method: 'get',
  });
}
