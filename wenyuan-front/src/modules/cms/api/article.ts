import request from '@/utils/request'

export function frontList(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/article/list`,
    params: params,
    method: 'get'
  })
}

export function frontGet(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/article/${id}`,
    method: 'get'
  })
}
export function list(params: object) {
  return request({
    url: 'cms/article/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `cms/article/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'cms/article/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'cms/article/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'cms/article/update',
    method: 'post',
    data
  })
}

/**
 * 导出模版
 * */
export function exportTemplate() {
  return request({
    url: 'cms/article/exportTemplate',
    method: 'get'
  })
}
