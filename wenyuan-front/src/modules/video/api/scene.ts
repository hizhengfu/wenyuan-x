import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'video/scene/list',
    params: params,
    method: 'get'
  })
}

/**
 * 根据场景获取视频列表
 * */
export function getSceneVideo(id: string) {
  return request({
    url: `video/scene/getSceneVideo/${id}`,
    method: 'get'
  })
}

/**
 * 保存场景视频
 * */
export function saveSceneVideo(id: string, data: string[]) {
  return request({
    url: `video/scene/saveSceneVideo/${id}`,
    method: 'post',
    data
  })
}

export function get(id: string) {
  return request({
    url: `video/scene/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'video/scene/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'video/scene/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'video/scene/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'video/scene/update',
    method: 'post',
    data
  })
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'video/scene/sort',
    method: 'post',
    data
  })
}

