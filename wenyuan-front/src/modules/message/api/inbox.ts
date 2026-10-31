import request from '@/utils/request';

/**
 * 通知中心 - 当前用户站内信收件箱
 *
 * 后端控制器路径前缀：${apiPath}/inbox
 */

/**
 * 分页查询当前用户站内信
 * @param params { pageNum, pageSize, readStatus?: 'UNREAD' | 'READ' }
 *  分页字段名跟项目通用 `GenericPage` 对齐：pageNum + pageSize；其他列表页同样使用。
 * */
export function findPage(params: object) {
  return request({
    url: 'inbox/page',
    method: 'get',
    params,
  });
}

/**
 * 未读数
 * */
export function unreadCount() {
  return request({
    url: 'inbox/unread-count',
    method: 'get',
  });
}

/**
 * 标记单条已读
 * */
export function read(id: string) {
  return request({
    url: `inbox/${id}/read`,
    method: 'post',
  });
}

/**
 * 全部已读
 * */
export function readAll() {
  return request({
    url: 'inbox/read-all',
    method: 'post',
  });
}
