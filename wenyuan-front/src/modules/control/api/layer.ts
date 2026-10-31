import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'control/layer/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `control/layer/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'control/layer/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'control/layer/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'control/layer/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'control/layer/update',
    method: 'post',
    data
  })
}



/**
 * 创建新的位置
 * */
export function listPoint(layerId: string, isDesign: boolean = false) {
  return request({
    url: 'control/layer/listPoint',
    method: 'get',
    params: {layerId: layerId, isDesign: isDesign}
  })
}

/**
 * 创建新的位置
 * */
export function addPoint(data: object) {
  return request({
    url: 'control/layer/addPoint',
    method: 'post',
    data
  })
}

/**
 * 删除点
 * */
export function removePoint(id: string) {
  return request({
    url: 'control/layer/removePoint',
    method: 'post',
    params: {id: id}
  })
}

/**
 * 更新位置
 * */
export function updatePoint(data: object) {
  return request({
    url: 'control/layer/updatePoint',
    method: 'post',
    data
  })
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'control/layer/sort',
    method: 'post',
    data,
  });
}
