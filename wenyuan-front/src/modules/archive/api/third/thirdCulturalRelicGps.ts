import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'archive/third/cultural/relic/gps/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `archive/third/cultural/relic/gps/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'archive/third/cultural/relic/gps/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'archive/third/cultural/relic/gps/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'archive/third/cultural/relic/gps/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'archive/third/cultural/relic/gps/update',
    method: 'post',
    data
  })
}

