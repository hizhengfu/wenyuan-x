import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'wms/artifact/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `wms/artifact/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'wms/artifact/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'wms/artifact/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'wms/artifact/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'wms/artifact/update',
    method: 'post',
    data,
  });
}

export function saveExtend(data: object) {
  return request({
    url: 'wms/artifact/updateExtend',
    method: 'post',
    data,
  });
}

export function getExtend(id: string) {
  return request({
    url: `wms/artifact/extend/${id}`,
    method: 'get',
  });
}

/**
 * 文物卡片导出 Word（按模板 / 单或批量）
 * options 中各布尔项可单独勾选「形制 / 封面 / 多角度 / 线图 / 平剖面」，
 * 未传时由后端按模板预设决定。返回的是 blob —— 由调用方触发下载。
 */
export function exportCard(
  ids: string[],
  template: string = 'STANDARD',
  options?: {
    includeShape?: boolean;
    includeCover?: boolean;
    includeAngles?: boolean;
    includeLine?: boolean;
    includeProfile?: boolean;
  },
) {
  return request({
    url: 'wms/artifact/exportCard',
    method: 'post',
    params: { template, ...(options || {}) },
    data: ids,
    responseType: 'blob',
  });
}
