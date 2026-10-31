import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'spider/wechat/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `spider/wechat/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'spider/wechat/create',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: 'spider/wechat/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'spider/wechat/update',
    method: 'post',
    data,
  });
}

// 预览来源中第一篇文章解析结果（不入库）
export function preview(id: string) {
  return request({
    url: 'spider/wechat/preview/' + id,
    method: 'get',
    timeout: 60000,
  });
}

// 立即采集并入库
export function collect(id: string) {
  return request({
    url: 'spider/wechat/collect/' + id,
    method: 'post',
    timeout: 120000,
  });
}

export function capabilities(): Promise<{ data: { automatic: boolean } }> {
  return request({ url: 'spider/wechat/capabilities/collection', method: 'get', silentError: true });
}
