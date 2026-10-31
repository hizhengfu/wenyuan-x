import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'four/cultural/record/main/wwdc/kgfj/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/main/wwdc/kgfj/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/main/wwdc/kgfj/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/main/wwdc/kgfj/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/main/wwdc/kgfj/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/main/wwdc/kgfj/update',
    method: 'post',
    data,
  });
}
