import request from '@/utils/request';

/**
 * 上传
 * */
export function upload() {
  return request({
    url: 'sys/deploy/upload',
    method: 'post',
  });
}

/**
 * 获取历史记录
 * */
export function history() {
  return request({
    url: 'sys/deploy/history',
    method: 'get',
  });
}

/**
 * 切换版本
 * */
export function switchDeploy(deployId: string) {
  return request({
    url: 'sys/deploy/switch/' + deployId,
    method: 'post',
  });
}

/**
 * 删除版本
 * */
export function deleteDeployId(deployId: string) {
  return request({
    url: 'sys/deploy/' + deployId,
    method: 'post',
  });
}
