import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'four/cultural/record/main/text/14/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/main/text/14/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/main/text/14/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/main/text/14/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/main/text/14/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/main/text/14/update',
    method: 'post',
    data,
  });
}
