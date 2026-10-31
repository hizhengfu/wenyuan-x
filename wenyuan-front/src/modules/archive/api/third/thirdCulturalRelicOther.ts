import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'archive/third/cultural/relic/other/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `archive/third/cultural/relic/other/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'archive/third/cultural/relic/other/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'archive/third/cultural/relic/other/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'archive/third/cultural/relic/other/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'archive/third/cultural/relic/other/update',
    method: 'post',
    data
  })
}

