import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'archive/third/cultural/relic/specimen/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `archive/third/cultural/relic/specimen/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'archive/third/cultural/relic/specimen/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'archive/third/cultural/relic/specimen/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'archive/third/cultural/relic/specimen/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'archive/third/cultural/relic/specimen/update',
    method: 'post',
    data
  })
}

