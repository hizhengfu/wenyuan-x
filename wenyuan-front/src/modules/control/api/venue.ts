import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'control/venue/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `control/venue/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'control/venue/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'control/venue/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'control/venue/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'control/venue/update',
    method: 'post',
    data
  })
}
