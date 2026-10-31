import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'control/device/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `control/device/${id}`,
    method: 'get'
  })
}

export function tree(params: object) {
  return request({
    url: 'control/device/tree',
    method: 'get',
    params
  })
}

/**
 * 获取指定节点下的数据
 * */
export function treeAll(params: object) {
  return request({
    url: 'control/device/treeAll',
    method: 'get',
    params
  })
}

export function add(data: any) {
  return request({
    url: 'control/device/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'control/device/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'control/device/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'control/device/update',
    method: 'post',
    data
  })
}


export function updateConfig(data: any) {
  return request({
    url: 'control/device/updateConfig',
    method: 'post',
    data
  })
}

