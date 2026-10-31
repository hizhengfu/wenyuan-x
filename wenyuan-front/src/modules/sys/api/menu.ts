import request from '@/utils/request'
import {transformToTreeFormat} from '@/utils/tree';


interface menuTreeParam {
  applicationId: string,
  front?: number
}

/**
 * 菜单信息
 * */
export function menuTree(params?: menuTreeParam) {
  return request({
    url: 'menu/tree',
    params: params || {applicationId: '1', front: 0},
    method: 'get'
  }).then((res) => {
    // return transformToTreeFormat(res.data, {idKey: 'id', pIdKey: 'parentId'})
    return res.data || []
  })
}


export function list(params: any) {
  return request({
    url: 'menu/list',
    params: params,
    method: 'get'
  })
}

/**
 * 获取收藏菜单
 * */
export function favorites(params: any) {
  return request({
    url: 'menu/favorites',
    params: params,
    method: 'get'
  })
}

/**
 * 切换收藏
 * */
export function toggleFavorite(id: string) {
  return request({
    url: `menu/favorite/${id}`,
    method: 'post'
  })
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'menu/sort',
    method: 'post',
    data
  })
}

export function tree(params?: object) {
  return request({
    url: 'menu/tree',
    params: params || {applicationId: '1', front: 0},
    method: 'get'
  }).then((res) => {
    return transformToTreeFormat(res.data, {idKey: 'id', pIdKey: 'parentId'})
  })
}

export function treeAll(params?: object) {
  return request({
    url: 'menu/treeAll',
    params: params || {applicationId: '1', front: 0},
    method: 'get'
  }).then((res) => {
    return transformToTreeFormat(res.data, {idKey: 'id', pIdKey: 'parentId'})
  })
}

export function get(id: string) {
  return request({
    url: `menu/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'menu/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'menu/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'menu/update',
    method: 'post',
    data
  })
}


export function copy(id: string) {
  return request({
    url: 'menu/copy/' + id,
    method: 'post',
  })
}
