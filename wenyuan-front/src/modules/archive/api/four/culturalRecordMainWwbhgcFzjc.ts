import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'four/cultural/record/main/wwbhgc/fzjc/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `four/cultural/record/main/wwbhgc/fzjc/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'four/cultural/record/main/wwbhgc/fzjc/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'four/cultural/record/main/wwbhgc/fzjc/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'four/cultural/record/main/wwbhgc/fzjc/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'four/cultural/record/main/wwbhgc/fzjc/update',
    method: 'post',
    data,
  });
}
