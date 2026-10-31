import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'knowledge/property/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `knowledge/property/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'knowledge/property/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'knowledge/property/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'knowledge/property/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'knowledge/property/update',
    method: 'post',
    data
  })
}

