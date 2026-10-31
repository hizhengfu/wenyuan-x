import request from '@/utils/request'

export function list(params: any) {
    return request({
      url: '/heritage/analyze/stat/areaTree',
      params: params,
      method: 'get'
    })
  }