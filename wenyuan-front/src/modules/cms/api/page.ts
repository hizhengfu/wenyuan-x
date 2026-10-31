import request from '@/utils/request';

export function frontList(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/page/list`,
    params: params,
    method: 'get',
  });
}

export function frontGet(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/page/${id}`,
    method: 'get',
  });
}

export function list(params: object) {
  return request({
    url: 'cms/page/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `cms/page/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'cms/page/create',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: 'cms/page/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'cms/page/update',
    method: 'post',
    data,
  });
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: `cms/page/sort`,
    method: 'post',
    data,
  });
}
