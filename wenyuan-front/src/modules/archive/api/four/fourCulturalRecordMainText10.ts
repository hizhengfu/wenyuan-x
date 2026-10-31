import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'four/cultural/record/main/text/10/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/main/text/10/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/main/text/10/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/main/text/10/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/main/text/10/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/main/text/10/update',
    method: 'post',
    data,
  });
}
