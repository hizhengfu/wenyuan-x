import request from '@/utils/request'

const base = 'video/camera/relation'

export function list(params: any) {
  return request({
    url: `${base}/list`,
    params,
    method: 'get'
  })
}

export function listByCamera(cameraId: string) {
  return request({
    url: `${base}/camera/${cameraId}`,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `${base}/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: `${base}/create`,
    method: 'post',
    data
  })
}

export function edit(data: any) {
  return request({
    url: `${base}/update`,
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: `${base}/delete/${id}`,
    method: 'post'
  })
}
