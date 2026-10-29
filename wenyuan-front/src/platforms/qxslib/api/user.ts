import request from '@/utils/request'

export function list(params: object) {
  return request({
    url: 'user/list',
    params: params,
    method: 'get'
  })
}

/**
 * 根据机构用户获取对应的用户列表
 * */
export function group(params: object) {
  return request({
    url: 'user/group',
    params: params,
    method: 'get'
  })
}

export function search(keywords: string) {
  return request({
    url: `user/search`,
    params: {keywords: keywords},
    method: 'get'
  })
}

/**
 * 获取用户的角色列表
 * */
export function roles(id: string) {
  return request({
    url: `user/roles`,
    params: {id: id},
    method: 'get'
  })
}

/**
 * 获取用户的岗位列表
 * */
export function posts(id: string) {
  return request({
    url: `user/posts`,
    params: {id: id},
    method: 'get'
  })
}

export function tree() {
  return request({
    url: 'user/tree',
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `user/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'user/create',
    method: 'post',
    data
  })
}

export function updateAvatar(data: object) {
  return request({
    url: 'user/updateAvatar',
    method: 'post',
    data
  })
}

export function updatePass(data: object) {
  return request({
    url: 'user/updatePass',
    method: 'post',
    data
  })
}

export function updateEmail(data: object) {
  return request({
    url: 'user/updateEmail',
    method: 'post',
    data
  })
}

export function resetEmail(params: object) {
  return request({
    url: 'code/resetEmail',
    method: 'get',
    params: params
  })
}

/**
 * 更新用户备注
 * */
export function updateRemarks(data: object) {
  return request({
    url: 'user/updateRemarks',
    method: 'post',
    data
  })
}

/**
 * 更新用户名
 * @param data {name:xxxx}
 * */
export function updateName(data: object) {
  return request({
    url: 'user/updateName',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'user/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'user/update',
    method: 'post',
    data
  })
}



