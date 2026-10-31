import request from '@/utils/request';

export function tree(params: object) {
  return request({
    url: 'four/cultural/record/menu/tree',
    method: 'get',
    params: params,
  });
}

export function treeFile(params: object) {
  return request({
    url: 'four/cultural/record/menu/treeFile',
    method: 'get',
    params: params,
  });
}


export function list(params: any) {
  return request({
    url: 'four/cultural/record/menu/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/menu/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/menu/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/menu/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/menu/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/menu/update',
    method: 'post',
    data,
  });
}
