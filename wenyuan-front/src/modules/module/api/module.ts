import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'module/list',
    params: params,
    method: 'get'
  })
}

//模块下的字段
export function columns(params: object) {
  return request({
    url: 'module/columns',
    params: params,
    method: 'get'
  })
}

//索引字段
export function index(params: object) {
  return request({
    url: 'module/index',
    params: params,
    method: 'get'
  })
}

//外键
export function reference(params: object) {
  return request({
    url: 'module/index',
    params: params,
    method: 'get'
  })
}

export function get(id: String) {
  return request({
    url: `module/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'module/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'module/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'module/update',
    method: 'post',
    data
  })
}

export function install(id: string) {
  return request({
    url: `module/install/${id}`,
    method: 'post'
  })
}
