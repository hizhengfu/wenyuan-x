import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'user/list',
    params: params,
    method: 'get',
  });
}

/**
 * 根据机构用户获取对应的用户列表
 * */
export function group(params: any) {
  return request({
    url: 'user/group',
    params: params,
    method: 'get',
  });
}

export function search(params: object) {
  return request({
    url: 'user/search',
    params: params,
    method: 'get',
  });
}

/**
 * 获取用户的角色列表
 * */
export function roles(id: string) {
  return request({
    url: 'user/roles',
    params: { id: id },
    method: 'get',
  });
}

/**
 * 获取用户的岗位列表
 * */
export function posts(id: string) {
  return request({
    url: 'user/posts',
    params: { id: id },
    method: 'get',
  });
}

export function tree() {
  return request({
    url: 'user/tree',
    method: 'get',
  });
}

/** Read-only display names for up to 100 IDs in the current account's scope. */
export function displayNames(ids: readonly string[]) {
  return request({ url: 'user/display-names', params: { ids: ids.join(',') }, method: 'get' });
}

export function get(id: string) {
  return request({
    url: `user/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'user/create',
    method: 'post',
    data,
  });
}

export function updateAvatar(data: object) {
  return request({
    url: 'user/updateAvatar',
    method: 'post',
    data,
  });
}

export function updatePass(data: object) {
  return request({
    url: 'user/updatePass',
    method: 'post',
    data,
  });
}

export function updateStatus(data: object) {
  return request({
    url: 'user/updateStatus',
    method: 'post',
    data,
  });
}

export function updateEmail(data: object) {
  return request({
    url: 'user/updateEmail',
    method: 'post',
    data,
  });
}

export function resetEmail(params: object) {
  return request({
    url: 'user/email/resetEmail',
    method: 'get',
    params: params,
  });
}

/**
 * 更新用户备注
 * */
export function updateRemarks(data: object) {
  return request({
    url: 'user/updateRemarks',
    method: 'post',
    data,
  });
}

/**
 * 更新用户名
 * @param data {name:xxxx}
 * */
export function updateName(data: object) {
  return request({
    url: 'user/updateName',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: 'user/delete/' + id,
    method: 'post',
  });
}

export function copy(id: string) {
  return request({
    url: 'user/copy/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'user/update',
    method: 'post',
    data,
  });
}

/**
 * 用户明细列表
 * */
export function detailList(params: object) {
  return request({
    url: 'user/detail/list',
    params: params,
    method: 'get',
  });
}

/**
 * 保存用户明细
 * */
export function saveDetail(data: object) {
  return request({
    url: 'user/detail/save',
    method: 'post',
    data,
  });
}

/**
 * 删除用户明细
 * */
export function delDetail(data: object) {
  return request({
    url: 'user/detail/del',
    method: 'post',
    data,
  });
}

/**
 * 生成token
 * */
export function generateToken(data: object) {
  return request({
    url: 'token/generate',
    method: 'post',
    data,
  });
}

/**
 * 登录记录
 * */
export function log(params: any) {
  return request({
    url: 'user/log',
    params: params,
    method: 'get',
  });
}
