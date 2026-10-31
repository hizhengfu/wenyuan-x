import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/visitor/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/visitor/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/visitor/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/visitor/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/visitor/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/visitor/update',
    method: 'post',
    data
  })
}

