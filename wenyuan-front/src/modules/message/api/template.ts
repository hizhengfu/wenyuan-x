import request from '@/utils/request';

/**
 * 通知中心 - 模板管理接口
 *
 * 后端控制器路径前缀：${apiPath}/notify-center/template
 */

export function findPage(params: object) {
  return request({
    url: 'notify-center/template/page',
    method: 'get',
    params,
  });
}

export function get(id: string) {
  return request({
    url: `notify-center/template/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'notify-center/template',
    method: 'post',
    data,
  });
}

export function edit(data: object) {
  return request({
    url: 'notify-center/template',
    method: 'post',
    data,
  });
}

export function del(id: string) {
  return request({
    url: `notify-center/template/${id}`,
    method: 'post',
  });
}

/**
 * 预览模板渲染结果（不发送）
 * @param data { id: string, params: Record<string, any> }
 * */
export function preview(data: object) {
  return request({
    url: 'notify-center/template/preview',
    method: 'post',
    data,
  });
}

/**
 * 试发：实际发送一条到当前登录用户
 * @param data { id: string, params: Record<string, any> }
 * */
export function testSend(data: object) {
  return request({
    url: 'notify-center/template/test-send',
    method: 'post',
    data,
  });
}
