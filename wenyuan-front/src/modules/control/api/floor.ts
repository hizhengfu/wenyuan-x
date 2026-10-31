import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'control/floor/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `control/floor/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'control/floor/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'control/floor/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'control/floor/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'control/floor/update',
    method: 'post',
    data
  })
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'control/floor/sort',
    method: 'post',
    data,
  });
}
