import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'wms/project/list',
    params: params,
    method: 'get',
  });
}

export function tree(params: object) {
  return request({
    url: 'wms/project/tree',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `wms/project/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'wms/project/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'wms/project/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'wms/project/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'wms/project/update',
    method: 'post',
    data,
  });
}
