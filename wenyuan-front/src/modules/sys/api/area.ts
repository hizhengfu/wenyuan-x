import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'area/list',
    params: params,
    method: 'get',
  });
}

export function tree(params: object) {
  return request({
    url: 'area/tree',
    params: params,
    method: 'get',
  }).then((res) => {
    return res.data;
  });
}

export function cascade(params: object) {
  return request({
    url: 'area/cascade',
    params: params,
    method: 'get',
  }).then((res) => {
    return res.data;
  });
}
export function cascadeFront(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/area/cascade`,
    params: params,
    method: 'get',
  }).then((res) => {
    return res.data;
  });
}

export function cascadeName(params: object) {
  return request({
    url: 'area/cascadeName',
    params: params,
    method: 'get',
  }).then((res) => {
    return res.data;
  });
}

export function get(id: string) {
  return request({
    url: `area/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'area/create',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: 'area/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'area/update',
    method: 'post',
    data,
  });
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'area/sort',
    method: 'post',
    data,
  });
}

export function listFront() {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/project/sys/group`,
    method: 'get',
  });
}
