import request from '@/utils/request';

export function frontList(params: object) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/site/list`,
    params: params,
    method: 'get',
  });
}

export function frontGet(id: string) {
  return request({
    url: `${APP_BASE_URL}${APP_API_FRONT_PATH}/cms/site/${id}`,
    method: 'get',
  });
}

export function list(params: object) {
  return request({
    url: 'cms/site/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `cms/site/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'cms/site/create',
    method: 'post',
    data,
  });
}

export function search(name: string) {
  return request({
    url: 'cms/site/search',
    params: { name: name },
    method: 'get',
  });
}

/**
 * 导出json文件
 * */
export function exportJsonZip(id: string) {
  return request({
    url: 'cms/site/json/' + id,
    method: 'get',
  });
}
/**
 * 服务端导出文件，对特别大的文件，前端导出不现实
 * */
export function exportServerFile(id: string) {
  return request({
    url: 'cms/site/file/' + id,
    method: 'get',
  });
}

export function toggle(id: string) {
  return request({
    url: 'cms/site/toggle/' + id,
    method: 'post',
  });
}

export function toggleBlack(id: string) {
  return request({
    url: 'cms/site/toggleBlack/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'cms/site/delete/' + id,
    method: 'post',
  });
}

export function copy(id: string) {
  return request({
    url: 'cms/site/copy/' + id,
    method: 'post',
  });
}

export function edit(data: string) {
  return request({
    url: 'cms/site/update',
    method: 'post',
    data,
  });
}
