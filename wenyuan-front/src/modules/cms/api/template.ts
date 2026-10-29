import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'cms/template/list',
    params: params,
    method: 'get'
  })
}

export function template(params: object) {
  return request({
    url: 'cms/template/template',
    params: params,
    method: 'get'
  })
}

export function theme(params: object) {
  return request({
    url: 'cms/template/theme',
    params: params,
    method: 'get'
  })
}
