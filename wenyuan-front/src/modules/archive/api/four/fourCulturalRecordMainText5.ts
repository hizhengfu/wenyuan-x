import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'four/cultural/record/main/text/5/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/main/text/5/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/main/text/5/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/main/text/5/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/main/text/5/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/main/text/5/update',
    method: 'post',
    data,
  });
}
