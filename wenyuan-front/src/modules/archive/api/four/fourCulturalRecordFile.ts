import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'four/cultural/record/file/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/file/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/file/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/file/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/file/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/file/update',
    method: 'post',
    data,
  });
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'four/cultural/record/file/sort',
    method: 'post',
    data
  })
}
