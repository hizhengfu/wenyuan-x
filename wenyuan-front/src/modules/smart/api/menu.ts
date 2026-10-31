import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'smart/control/menu/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `smart/control/menu/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'smart/control/menu/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'smart/control/menu/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'smart/control/menu/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'smart/control/menu/update',
    method: 'post',
    data
  })
}
