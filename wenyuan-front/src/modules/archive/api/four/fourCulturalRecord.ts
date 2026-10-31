import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'four/cultural/record/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/update',
    method: 'post',
    data,
  });
}

export function geoJson(params: object) {
  return request({
    url: 'four/cultural/record/geoJson',
    method: 'get',
    params,
  });
}

export function batchImport() {
  return request({
    url: 'four/cultural/record/batchImport',
    method: 'post',
  });
}
