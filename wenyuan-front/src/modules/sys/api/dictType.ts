import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'dictType/list',
    params: params,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'dictType/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'dictType/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'dictType/update',
    method: 'post',
    data
  })
}
