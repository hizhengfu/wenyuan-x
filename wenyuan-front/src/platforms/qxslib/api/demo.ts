import request from '@/utils/request'


/**
 * 机器人状态
 * */
export function robotState() {
  return request({
    url: 'api/robot/group/',
    method: "get"
  })
}

export function rasterGroup() {
  return request({
    url: 'api/raster/group/',
    method: "get"
  })
}


export function sendTAsk(data: any) {
  return request({
    url: 'api/task/',
    data,
    method: "post"
  })
}

