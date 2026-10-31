import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'archive/third/cultural/relic/basic/list',
    params: params,
    method: 'get'
  })
}

export function get(id: any) {
  return request({
    url: `archive/third/cultural/relic/basic/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'archive/third/cultural/relic/basic/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'archive/third/cultural/relic/basic/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'archive/third/cultural/relic/basic/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'archive/third/cultural/relic/basic/update',
    method: 'post',
    data
  })
}

