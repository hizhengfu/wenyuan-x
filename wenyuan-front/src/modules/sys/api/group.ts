import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'group/list',
    params: params,
    method: 'get'
  })
}

export function tree(params: object) {
  return request({
    url: 'group/tree',
    params: params,
    method: 'get'
  }).then((res) => {
    return res.data
  })
}

export function get(id: string) {
  return request({
    url: `group/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'group/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'group/delete/' + id,
    method: 'post'
  })
}

export function copy(id: string) {
  return request({
    url: 'group/copy/' + id,
    method: 'post',
  })
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: `group/sort`,
    method: 'post',
    data
  })
}

export function edit(data: object) {
  return request({
    url: 'group/update',
    method: 'post',
    data
  })
}

export function getName(params: object) {
  return request({
    url: 'sys/sysGroup/getname',
    method: 'get',
    params: params
  })
}

export function getById(id: string) {
  return request({
    url: 'sys/sysGroup/get/' + id,
    method: 'get'
  })
}

