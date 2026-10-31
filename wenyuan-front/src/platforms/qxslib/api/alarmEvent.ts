import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/alarm/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/alarm/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/alarm/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/alarm/copy/' + id,
    method: 'post'
  })
}


export function deleteAll() {
  return request({
    url: 'museum/alarm/deleteAll/',
    method: 'post'
  })
}

export function del(id: string) {
  return request({
    url: 'museum/alarm/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/alarm/update',
    method: 'post',
    data
  })
}

/**
 * 异常处理
 * */
export function dispose(data: any) {
  return request({
    url: 'museum/alarm/dispose',
    method: 'post',
    data
  })
}

/**
 * 异常处理日志
 * */
export function logs(id: string) {
  return request({
    url: 'museum/alarm/logs',
    method: 'get',
    params: {id: id}
  })
}


