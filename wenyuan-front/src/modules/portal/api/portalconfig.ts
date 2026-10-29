import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'portal/config/list',
    params: params,
    method: 'get'
  })
}

export function del(id: string) {
  return request({
    url: 'portal/config/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'portal/config/update',
    method: 'post',
    data
  })
}
