import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'cultural/museum/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `cultural/museum/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'cultural/museum/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'cultural/museum/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'cultural/museum/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'cultural/museum/update',
    method: 'post',
    data
  })
}
