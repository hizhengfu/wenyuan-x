import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'wms/project/cell/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `wms/project/cell/${id}`,
    method: 'get',
  });
}

export function tree(params: object) {
  return request({
    url: 'wms/project/cell/tree',
    params: params,
    method: 'get',
  });
}

export function plot(params: object) {
  return request({
    url: 'wms/project/cell/plot',
    params: params,
    method: 'get',
  });
}

/**
 * 更新探方与遗迹的关系
 * */
export function updatePlot(data: object) {
  return request({
    url: 'wms/project/cell/plot',
    data: data,
    method: 'post',
  });
}

export function add(data: object) {
  return request({
    url: 'wms/project/cell/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'wms/project/cell/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'wms/project/cell/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'wms/project/cell/update',
    method: 'post',
    data,
  });
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: `wms/project/cell/sort`,
    method: 'post',
    data,
  });
}
