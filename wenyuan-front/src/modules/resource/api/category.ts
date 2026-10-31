import request from '@/utils/request';

/**
 * 分类分页列表
 */
export function list(params: any) {
  return request({
    url: 'resource/category/list',
    method: 'get',
    params,
  });
}

/**
 * 分类详情
 */
export function get(id: string) {
  return request({
    url: `resource/category/${id}`,
    method: 'get',
  });
}

/** 新建分类 */
export function add(data: any) {
  return request({
    url: 'resource/category/create',
    method: 'post',
    data,
  });
}

/** 更新分类 */
export function edit(data: any) {
  return request({
    url: 'resource/category/update',
    method: 'post',
    data,
  });
}

/** 删除分类 */
export function del(id: string) {
  return request({
    url: `resource/category/delete/${id}`,
    method: 'post',
  });
}

/** 分类树 */
export function tree(params?: any) {
  return request({
    url: 'resource/category/tree',
    method: 'get',
    params,
  });
}

/** 子分类列表 */
export function children(parentId: string) {
  return request({
    url: `resource/category/children/${parentId}`,
    method: 'get',
  });
}

/** 所有分类（扁平化，下拉选择用） */
export function all(params?: any) {
  return request({
    url: 'resource/category/all',
    method: 'get',
    params,
  });
}
