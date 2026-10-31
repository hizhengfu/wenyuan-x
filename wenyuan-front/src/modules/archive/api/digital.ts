import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'archive/digital/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `archive/digital/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'archive/digital/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'archive/digital/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'archive/digital/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'archive/digital/update',
    method: 'post',
    data
  })
}

