import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'print/client/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `print/client/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'print/client/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'print/client/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'print/client/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'print/client/update',
    method: 'post',
    data,
  });
}
