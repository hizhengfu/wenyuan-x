import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/tour/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/tour/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/tour/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/tour/copy/' + id,
    method: 'post'
  })
}

export function syncData() {
  return request({
    url: 'museum/tour/syncData',
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/tour/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/tour/update',
    method: 'post',
    data
  })
}

