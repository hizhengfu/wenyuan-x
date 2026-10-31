import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'cultural/basic/area/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `cultural/basic/area/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'cultural/basic/area/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'cultural/basic/area/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'cultural/basic/area/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'cultural/basic/area/update',
    method: 'post',
    data,
  });
}
