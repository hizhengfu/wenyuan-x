import request from '@/utils/request'

export function frontList(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/project/list`,
    params: params,
    method: 'get'
  })
}

export function frontGet(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/project/${id}`,
    method: 'get'
  })
}
export function list(params: object) {
  return request({
    url: 'cms/project/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `cms/project/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'cms/project/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'cms/project/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'cms/project/update',
    method: 'post',
    data
  })
}

export function updateSort(data: object) {
  return request({
    url: 'cms/project/sort',
    method: 'post',
    data
  })
}
