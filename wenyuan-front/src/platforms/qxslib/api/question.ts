import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/question/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/question/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/question/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/question/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/question/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/question/update',
    method: 'post',
    data
  })
}

