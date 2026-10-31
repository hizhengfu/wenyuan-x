import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'sys/feedback/list',
    params: params,
    method: 'get',
  });
}

/**
 * 查询自身的反馈数据
 * */
export function listSelf(params: any) {
  return request({
    url: 'sys/feedback/self',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `sys/feedback/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'sys/feedback/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'sys/feedback/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'sys/feedback/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'sys/feedback/update',
    method: 'post',
    data,
  });
}

export function replay(data: any) {
  return request({
    url: 'sys/feedback/updateReplay',
    method: 'post',
    data,
  });
}
