import request from '@/utils/request';

/**
 * 上传
 * */
export function upload() {
  return request({
    url: 'sys/deploy/backend/upload',
    method: 'post',
  });
}

/**
 * 获取历史记录
 * */
export function history() {
  return request({
    url: 'sys/deploy/backend/history',
    method: 'get',
  });
}
