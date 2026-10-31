import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'archive/third/cultural/relic/cover/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `archive/third/cultural/relic/cover/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'archive/third/cultural/relic/cover/create',
    method: 'post',
    data,
  });
}
export function culturalCreate(data: object) {
  return request({
    url: 'archive/third/cultural/relic/cover/culturalCreate',
    method: 'post',
    data,
  });
}


export function copy(id: string) {
  return request({
    url: 'archive/third/cultural/relic/cover/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'archive/third/cultural/relic/cover/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'archive/third/cultural/relic/cover/update',
    method: 'post',
    data,
  });
}

export function batchImport() {
  return request({
    url: 'archive/third/cultural/relic/cover/batchImport',
    method: 'post',
  });
}
