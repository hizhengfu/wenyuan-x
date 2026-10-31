import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'post/list',
    params: params,
    method: 'get'
  })
}

export function all() {
  return request({
    url: 'post/all',
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `post/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'post/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'post/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'post/update',
    method: 'post',
    data
  })
}
