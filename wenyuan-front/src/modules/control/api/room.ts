import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'control/room/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `control/room/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'control/room/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'control/room/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'control/room/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'control/room/update',
    method: 'post',
    data
  })
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'control/room/sort',
    method: 'post',
    data,
  });
}
