import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'video/group/list',
    params: params,
    method: 'get'
  })
}

export function tree(params: any) {
  return request({
    url: 'video/group/tree',
    params: params,
    method: 'get'
  })
}

/**
 * 机构下的摄像头
 * */
export function camera(id: string) {
  return request({
    url: 'video/group/camera',
    params: {id: id},
    method: 'get'
  })
}


export function get(id: string) {
  return request({
    url: `video/group/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'video/group/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'video/group/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'video/group/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'video/group/update',
    method: 'post',
    data
  })
}

export function basicList(params: any) {
  return request({
    url: `cultural/basic/list`,
    method: 'get',
    params: params
  });
}

export function videoRelationTree(params: object) {
  return request({
    url: 'cultural/basic/videoRelationTree',
    method: 'GET',
    params,
  });
}
