import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'job/list',
    params: params,
    method: 'get'
  })
}

/**
 * 查看日志
 * */
export function logs(params: object) {
  return request({
    url: 'job/logs',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `job/${id}`,
    method: 'post'
  })
}

export function add(data: object) {
  return request({
    url: 'job/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'job/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'job/update',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'job/copy/' + id,
    method: 'post',
  })
}

/**
 * 执行job
 * */
export function exec(id: string) {
  return request({
    url: `job/exec/${id}`,
    method: 'post'
  })
}

/**
 * 更改执行状态
 * */
export function state(id: string) {
  return request({
    url: `job/state/${id}`,
    method: 'post'
  })
}
