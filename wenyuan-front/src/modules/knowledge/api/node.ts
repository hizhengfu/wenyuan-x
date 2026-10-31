import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'knowledge/node/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `knowledge/node/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'knowledge/node/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'knowledge/node/copy/' + id,
    method: 'post'
  })
}

export function del(id: string) {
  return request({
    url: 'knowledge/node/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'knowledge/node/update',
    method: 'post',
    data
  })
}
