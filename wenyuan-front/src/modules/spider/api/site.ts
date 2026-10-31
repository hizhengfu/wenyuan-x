import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'spider/site/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `spider/site/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'spider/site/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'spider/site/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'spider/site/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'spider/site/update',
    method: 'post',
    data
  })
}
