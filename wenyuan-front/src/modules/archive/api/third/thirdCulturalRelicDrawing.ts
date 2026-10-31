import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'archive/third/cultural/relic/drawing/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `archive/third/cultural/relic/drawing/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'archive/third/cultural/relic/drawing/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'archive/third/cultural/relic/drawing/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'archive/third/cultural/relic/drawing/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'archive/third/cultural/relic/drawing/update',
    method: 'post',
    data
  })
}

