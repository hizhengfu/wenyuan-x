import request from '@/utils/request'

export function frontList(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/category/list`,
    params: params,
    method: 'get'
  })
}

export function frontGet(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/category/${id}`,
    method: 'get'
  })
}

export function list(params: object) {
  return request({
    url: 'cms/category/list',
    params: params,
    method: 'get'
  })
}

export function tree(params: object) {
  return request({
    url: 'cms/category/tree',
    params: params,
    method: 'get'
  })
}

export function cascade(params: object) {
  return request({
    url: 'cms/category/cascade',
    params: params,
    method: 'get'
  }).then((res) => {
    return res.data
  })
}

export function get(id: string) {
  return request({
    url: `cms/category/${id}`,
    method: 'get'
  })
}

export function sort(data: object) {
  return request({
    url: 'cms/category/sort',
    method: 'post',
    data
  })
}

export function add(data: object) {
  return request({
    url: 'cms/category/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'cms/category/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'cms/category/update',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'cms/category/copy/' + id,
    method: 'post'
  })
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: `cms/category/sort`,
    method: 'post',
    data
  })
}
