import request from '@/utils/request.ts';

export function get(id: string, type: string) {
  return request({
    url: 'wms/operation/' + id,
    method: 'get',
    params: { type: type },
  });
}

export function list(params: object) {
  return request({
    url: 'wms/operation/list',
    method: 'get',
    params,
  });
}

export function history(params: object) {
  return request({
    url: 'wms/operation/history',
    method: 'get',
    params,
  });
}

/**
 * 获取文物列表
 * */
export function artifactList(params: object) {
  return request({
    url: 'wms/operation/artifactList',
    method: 'get',
    params,
  });
}

export function inbound(data: object) {
  return request({
    url: 'wms/operation/inbound',
    method: 'post',
    data,
  });
}

export function outbound(data: object) {
  return request({
    url: 'wms/operation/outbound',
    method: 'post',
    data,
  });
}

/**
 * 移库操作
 * */
export function transfer(data: object) {
  return request({
    url: 'wms/operation/transfer',
    method: 'post',
    data,
  });
}

/**
 * 归库操作
 * */
export function returnOperation(data: object) {
  return request({
    url: 'wms/operation/return',
    method: 'post',
    data,
  });
}
