import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/rule/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/rule/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/rule/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/rule/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/rule/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/rule/update',
    method: 'post',
    data
  })
}

