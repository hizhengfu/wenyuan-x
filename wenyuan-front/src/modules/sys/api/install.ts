import request from '@/utils/request'

export function reset(params: any) {
  return request({
    url: 'install/reset',
    params: params,
    method: 'get'
  })
}

export function resetDict() {
  return request({
    url: 'install/resetDict',
    method: 'get'
  })
}

export function resetMenu() {
  return request({
    url: 'install/resetMenu',
    method: 'get'
  })
}

export function resetPermission() {
  return request({
    url: 'install/resetPermission',
    method: 'get'
  })
}

export function syncPermission() {
  return request({
    url: 'install/syncPermission',
    method: 'post'
  })
}

/**
 * 重置地区
 * */
export function resetArea() {
  return request({
    url: 'area/init',
    method: 'post'
  })
}

/**
 * 重置街道信息
 * */
export function initStreet(params: object) {
  return request({
    url: 'area/initStreet',
    method: 'post',
    params
  })
}

export function update(params: any) {
  return request({
    url: 'install/update',
    params: params,
    method: 'get'
  })
}
