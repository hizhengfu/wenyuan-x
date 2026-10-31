import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'video/camera/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `video/camera/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'video/camera/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'video/camera/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'video/camera/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'video/camera/update',
    method: 'post',
    data
  })
}

