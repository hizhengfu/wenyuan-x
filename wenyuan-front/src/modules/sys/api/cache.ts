import request from '@/utils/request'

export function list() {
  return request({
    url: 'cache/list',
    method: 'get'
  })
}

export function listKey(name: string) {
  return request({
    url: `cache/list/${name}`,
    method: 'get'
  })
}

export function delName(name: string) {
  return request({
    url: `cache/delete/${name}`,
    method: 'post'
  })
}

export function delKey(name: string, key: string) {
  return request({
    url: `cache/delete/${name}/${key}`,
    method: 'post'
  })
}

export function clear() {
  return request({
    url: `cache/clear`,
    method: 'post'
  })
}
