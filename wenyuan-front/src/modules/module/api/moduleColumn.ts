import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'module/column/list',
    params: params,
    method: 'get'
  })
}

export function updateSort(data: object) {
  return request({
    url: 'module/column/sort',
    method: 'post',
    data
  })
}

export function get(id: string) {
  return request({
    url: `module/column/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'module/column/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'module/column/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'module/column/update',
    method: 'post',
    data
  })
}

/**
 * 部分更新
 * */
export function partialUpdate(data: any) {
  return request({
    url: 'module/column/partialUpdate',
    method: 'post',
    data
  })
}
