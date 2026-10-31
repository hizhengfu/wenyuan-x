import request from '@/utils/request';

/**
 * 通知中心 - 当前用户订阅偏好
 *
 * 后端控制器路径前缀：${apiPath}/notify-center/subscription
 */

/**
 * 查询本人各 bizType 的关闭通道
 * */
export function get() {
  return request({
    url: 'notify-center/subscription',
    method: 'get',
  });
}

/**
 * 批量保存偏好
 * @param data { items: Array<{ bizType: string, disabledChannels: string[] }> }
 * */
export function update(data: object) {
  return request({
    url: 'notify-center/subscription',
    method: 'post',
    data,
  });
}
