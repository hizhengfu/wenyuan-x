import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'four/cultural/record/assistant/xzglwj/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/assistant/xzglwj/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/assistant/xzglwj/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/assistant/xzglwj/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/assistant/xzglwj/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/assistant/xzglwj/update',
    method: 'post',
    data,
  });
}
