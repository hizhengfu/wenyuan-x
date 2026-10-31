import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'sys/tag/category/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `sys/tag/category/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'sys/tag/category/create',
    method: 'post',
    data,
  });
}

export function edit(data: object) {
  return request({
    url: 'sys/tag/category/update',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: 'sys/tag/category/delete/' + id,
    method: 'post',
  });
}

/** 全量列表（前端 select 加载选项 / TagSelect 分组用） */
export function all() {
  return request({
    url: 'sys/tag/category/all',
    method: 'get',
  });
}
