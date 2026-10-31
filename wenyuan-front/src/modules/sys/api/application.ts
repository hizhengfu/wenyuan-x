import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'application/list',
    params: params,
    method: 'get'
  })
}

export function all(params: object) {
  return request({
    url: 'application/all',
    params: params,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'application/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'application/delete/' + id,
    method: 'post'
  })
}

export function resetAppSecret(id: string) {
  return request({
    url: 'application/resetAppSecret/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'application/update',
    method: 'post',
    data
  })
}
