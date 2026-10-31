import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/place/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/place/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/place/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/place/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/place/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/place/update',
    method: 'post',
    data
  })
}

