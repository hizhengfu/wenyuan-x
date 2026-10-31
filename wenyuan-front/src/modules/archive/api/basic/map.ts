import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: `/cultural/basic/area/stat/applist`,
    params: params,
    method: 'get'
  })
}
export function culturalList(params: any) {
  return request({
    url: `/cultural/basic/list`,
    params: params,
    method: 'get'
  })
}