import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'knowledge/relationship/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `knowledge/relationship/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'knowledge/relationship/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'knowledge/relationship/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'knowledge/relationship/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'knowledge/relationship/update',
    method: 'post',
    data
  })
}

