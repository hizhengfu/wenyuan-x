import request from '@/utils/request';

export function list(params: object) {
  return request({
    url: 'sys/tag/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `sys/tag/${id}`,
    method: 'get',
  });
}

export function add(data: object) {
  return request({
    url: 'sys/tag/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'sys/tag/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'sys/tag/delete/' + id,
    method: 'post',
  });
}

export function edit(data: object) {
  return request({
    url: 'sys/tag/update',
    method: 'post',
    data,
  });
}

/**
 * 查询某业务对象已绑定的标签列表（前端 TagSelect 加载已选用）。
 * @param params { contentId, type }
 */
export function getTagsOf(params: { contentId: string; type?: string }) {
  return request({
    url: 'sys/tag/tags',
    params,
    method: 'get',
  });
}

/**
 * 批量查询多个业务对象的标签（业务列表 JOIN 场景用）。
 * 返回 Map<contentId, TagVo[]>
 */
export function findTagsByContentIds(data: { contentIds: string[]; type?: string }) {
  return request({
    url: 'sys/tag/tags/batch',
    method: 'post',
    data,
  });
}

/**
 * 反查：按标签筛选业务对象 ID 列表。
 */
export function findContentIdsByTags(params: { tagIds: string[]; type?: string; matchAll?: boolean }) {
  return request({
    url: 'sys/tag/contents',
    params,
    method: 'get',
  });
}

/**
 * 保存业务对象与标签的关联（业务表单单独提交标签时用，业务表单同提交时建议走业务接口由后端 bindTags）。
 */
export function bindTags(data: { contentId: string; list: string[]; type?: string }) {
  return request({
    url: 'sys/tag/associate',
    method: 'post',
    data,
  });
}
