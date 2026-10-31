import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'knowledge/category/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `knowledge/category/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'knowledge/category/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'knowledge/category/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'knowledge/category/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'knowledge/category/update',
    method: 'post',
    data
  })
}

