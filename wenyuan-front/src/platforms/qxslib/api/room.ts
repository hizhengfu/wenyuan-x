import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/room/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/room/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/room/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/room/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/room/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/room/update',
    method: 'post',
    data
  })
}

