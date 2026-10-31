import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/layer/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/layer/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/layer/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/layer/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/layer/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'museum/layer/update',
    method: 'post',
    data
  })
}

/**
 * 创建新的位置
 * */
export function listPoint(layerId: string, isDesign: boolean = false) {
  return request({
    url: 'museum/layer/listPoint',
    method: 'get',
    params: {layerId: layerId, isDesign: isDesign}
  })
}

/**
 * 创建新的位置
 * */
export function addPoint(data: object) {
  return request({
    url: 'museum/layer/addPoint',
    method: 'post',
    data
  })
}

/**
 * 删除点
 * */
export function removePoint(id: string) {
  return request({
    url: 'museum/layer/removePoint',
    method: 'post',
    params: {id: id}
  })
}

/**
 * 更新位置
 * */
export function updatePoint(data: object) {
  return request({
    url: 'museum/layer/updatePoint',
    method: 'post',
    data
  })
}

