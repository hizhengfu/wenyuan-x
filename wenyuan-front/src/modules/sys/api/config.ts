import request from '@/utils/request';

/**
 * 配置信息
 * */
export function all() {
  return request({
    url: 'config/all',
    method: 'get',
  });
}

export function list(params: object) {
  return request({
    url: 'config/list',
    params: params,
    method: 'get',
  });
}

/**
 * 返回存在 schema 的模块 id 列表（distinct），用于渲染 tab。
 */
export function modules() {
  return request({
    url: 'config/modules',
    method: 'get',
  });
}

/**
 * 按需拉取指定模块的 schema 行（点击 tab 时调用）。
 */
export function moduleRows(moduleId: string) {
  return request({
    url: `config/modules/${moduleId}`,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `config/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'config/create',
    method: 'post',
    data,
  });
}

export function edit(data: object) {
  return request({
    url: 'config/update',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: 'config/delete/' + id,
    method: 'post',
  });
}


export function save(data: object) {
  return request({
    url: 'config/save',
    method: 'post',
    data,
  });
}

