import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'smart/control/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `smart/control/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'smart/control/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'smart/control/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'smart/control/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'smart/control/update',
    method: 'post',
    data
  })
}

