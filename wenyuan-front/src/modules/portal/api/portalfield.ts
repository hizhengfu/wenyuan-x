import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'portal/field/list',
    params: params,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'portal/field/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'portal/field/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'portal/field/update',
    method: 'post',
    data
  })
}

export function updateSort(data: object) {
  return request({
    url: 'portal/field/sort',
    method: 'post',
    data
  })
}
