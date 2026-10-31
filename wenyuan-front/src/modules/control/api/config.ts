import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'control/config/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `control/config/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'control/config/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'control/config/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'control/config/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'control/config/update',
    method: 'post',
    data
  })
}

