import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'portal/define/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `portal/define/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'portal/define/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'portal/define/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'portal/define/update',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'portal/define/copy/' + id,
    method: 'post'
  })
}
