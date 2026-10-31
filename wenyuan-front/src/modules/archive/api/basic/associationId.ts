import request from '@/utils/request'

export function thirdList(params: any) {
    return request({
      url: '/archive/third/cultural/relic/cover/list',
      params: params,
      method: 'get'
    })
  }
export function rollList(params: any) {
    return request({
      url: 'four/cultural/record/list',
      params: params,
      method: 'get'
    })
  }
  
export function fourthList(params: any) {
    return request({
      url: 'fourth/cultural/relic/cover/list',
      params: params,
      method: 'get'
    })
  }
export function singleSiteList(params: any) {
    return request({
      url: 'monitor/station/list',
      params: params,
      method: 'get'
    })
  }
  