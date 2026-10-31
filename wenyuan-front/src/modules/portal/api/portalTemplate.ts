import request from '@/utils/request'

/**
 * 加载用户自定义Portal
 * */
export function load(params: object) {
  return request({
    url: 'portal/template/load',
    params: params,
    method: 'get'
  })
}

/**
 * 加载用户自定义Portal
 * */
export function loadSelf(params: object) {
  return request({
    url: 'portal/template/loadSelf',
    params: params,
    method: 'get'
  })
}

/**
 * 加载用户可选的Portal
 * */
export function list(params: object) {
  return request({
    url: 'portal/template/list',
    params: params,
    method: 'get'
  })
}

/**
 * 加载用户有权限的所有的菜单列表
 * */
export function menus(params: object) {
  return request({
    url: 'portal/template/menus',
    params: params,
    method: 'get'
  })
}

/**
 * 更新Portal布局
 * */
export function updateLayout(data: object) {
  return request({
    url: 'portal/template/updateLayout',
    data: data,
    method: 'post'
  })
}

/**
 * 创建Portal布局
 * */
export function create(data: object) {
  return request({
    url: 'portal/template/create',
    data: data,
    method: 'post'
  })
}

/**
 * 删除Portal布局
 * */
export function del(id: string) {
  return request({
    url: `portal/template/delete/${id}`,
    method: 'post'
  })
}
