import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'fourth/cultural/relic/cover/list',
    params: params,
    method: 'get'
  })
}

export function get(id: any) {
  return request({
    url: `fourth/cultural/relic/cover/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'fourth/cultural/relic/cover/create',
    method: 'post',
    data
  })
}
export function culturalCreate(data: object) {
  return request({
    url: 'fourth/cultural/relic/cover/culturalCreate',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'fourth/cultural/relic/cover/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'fourth/cultural/relic/cover/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'fourth/cultural/relic/cover/update',
    method: 'post',
    data
  })
}

export function batchImport() {
  return request({
    url: 'fourth/cultural/relic/cover/batchImport',
    method: 'post',
  });
}

