import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/robot/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/robot/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/robot/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/robot/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/robot/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/robot/update',
    method: 'post',
    data
  })
}

