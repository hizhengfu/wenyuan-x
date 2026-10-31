import request from '@/utils/request';

/**
 * 事件订阅查询
 * */
export function list(serveId: string) {
  return request({
    url: 'video/hik/event/subscribe/' + serveId,
    method: 'get',
  });
}

/**
 * 事件订阅
 * */
export function createEvent(serveId: string, data: any) {
  return request({
    url: 'video/hik/event/subscribe/' + serveId,
    data: data,
    method: 'post',
  });
}

/**
 * 取消订阅
 * */
export function cancelEvent(serveId: string, data: any) {
  return request({
    url: 'video/hik/event/unSubscribe/' + serveId,
    data: data,
    method: 'post',
  });
}

/**
 * 事件查询
 * */
export function queryEvent(serveId: string, data: any) {
  return request({
    url: 'video/hik/event/query/' + serveId,
    data: data,
    method: 'post',
  });
}
