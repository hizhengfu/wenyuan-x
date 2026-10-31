import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'control/command/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `control/command/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'control/command/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'control/command/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'control/command/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'control/command/update',
    method: 'post',
    data
  })
}

export function saveBatch(data: any) {
  return request({
    url: 'control/command/saveBatch',
    method: 'post',
    data
  })
}

