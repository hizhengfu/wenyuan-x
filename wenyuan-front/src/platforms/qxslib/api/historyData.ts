import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/data/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/data/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/data/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/data/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/data/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/data/update',
    method: 'post',
    data
  })
}

