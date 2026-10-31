import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'portal/group/list',
    params: params,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'portal/group/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'portal/group/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'portal/group/update',
    method: 'post',
    data
  })
}

export function get(id: string) {
  return request({
    url: 'portal/group/' + id,
    method: 'get'
  })
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: `portal/group/sort`,
    method: 'post',
    data
  })
}
