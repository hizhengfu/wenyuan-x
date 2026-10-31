import request from '@/utils/request'

/**
 * 加载用户自定义Portal
 * */
export function load(params: object) {
  return request({
    url: 'portal/load',
    params: params,
    method: 'get'
  })
}

/**
 * 加载用户可选的Portal
 * */
export function list(params: object) {
  return request({
    url: 'portal/list',
    params: params,
    method: 'get'
  })
}

/**
 * 更新Portal布局
 * */
export function updateLayout(data: object) {
  return request({
    url: 'portal/updateLayout',
    data: data,
    method: 'post'
  })
}

/**
 * 更新Portal布局
 * */
export function create(data: object) {
  return request({
    url: 'portal/create',
    data: data,
    method: 'post'
  })
}

/**
 * 更新Portal布局
 * */
export function del(id: string) {
  return request({
    url: `portal/delete/${id}`,
    method: 'post'
  })
}

/**
 * 重置用户Portal模板
 * */
export function reset() {
  return request({
    url: 'portal/reset',
    method: 'get'
  })
}

export function checkCount(params: object) {
  return request({
    url: 'relics/portlet/checkcount',
    params: params,
    method: 'get'
  })
}
