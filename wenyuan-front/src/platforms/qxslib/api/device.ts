import request from '@/utils/request'

export function list(params: any) {
  return request({url: 'museum/device/list', params: params, method: 'get'})
}

export function get(id: string) {
  return request({url: `museum/device/${id}`, method: 'get'})
}

export function add(data: any) {
  return request({url: 'museum/device/create', method: 'post', data})
}

export function copy(id: string) {
  return request({url: 'museum/device/copy/' + id, method: 'post'})
}

export function del(id: string) {
  return request({url: 'museum/device/delete/' + id, method: 'post'})
}

export function edit(data: any) {
  return request({url: 'museum/device/update', method: 'post', data})
}
