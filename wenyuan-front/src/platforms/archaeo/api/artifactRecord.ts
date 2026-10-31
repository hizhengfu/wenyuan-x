import request from '@/utils/request.ts';

/**
 * 操作记录
 * */
export function list(params: object) {
  return request({
    url: 'wms/artifact/record/list',
    method: 'get',
    params,
  });
}
