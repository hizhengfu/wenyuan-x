import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'control/protocol/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `control/protocol/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'control/protocol/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'control/protocol/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'control/protocol/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'control/protocol/update',
    method: 'post',
    data
  })
}

