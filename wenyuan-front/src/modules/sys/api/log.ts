import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'log/list',
    method: 'get',
    params: params
  })
}

export function codeList(params: object) {
  return request({
    url: 'sys/verificationCode/list',
    method: 'get',
    params: params
  })
}
