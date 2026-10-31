import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'video/config/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `video/config/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'video/config/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'video/config/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'video/config/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'video/config/update',
    method: 'post',
    data
  })
}

export function sync(serveId: string) {
  return request({
    url: 'video/hik/sync/' + serveId,
    method: 'post',
  })
}

export function clearData(serveId: string) {
  return request({
    url: 'video/config/clearData/' + serveId,
    method: 'post',
  })
}
