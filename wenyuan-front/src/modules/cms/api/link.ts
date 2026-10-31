import request from '@/utils/request'

export function frontList(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/link/list`,
    params: params,
    method: 'get'
  })
}

export function frontGet(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/link/${id}`,
    method: 'get'
  })
}

export function list(params: object) {
  return request({
    url: 'cms/link/list',
    params: params,
    method: 'get'
  })
}

export function updateSort(data: object) {
  return request({
    url: 'cms/link/sort',
    method: 'post',
    data
  })
}

export function get(id: string) {
  return request({
    url: `cms/link/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'cms/link/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'cms/link/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'cms/link/update',
    method: 'post',
    data
  })
}
