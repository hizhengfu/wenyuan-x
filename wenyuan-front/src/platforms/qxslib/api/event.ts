import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/event/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/event/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/event/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/event/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/event/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/event/update',
    method: 'post',
    data
  })
}

/**
 * 执行任务
 * */
export function process(id: string) {
  return request({
    url: 'museum/event/process/' + id,
    method: 'post',
  })
}

