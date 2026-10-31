import request from '@/utils/request'


export function list(params: any) {
  return request({
    url: 'comment/list',
    params: params,
    method: 'get'
  })
}


/**
 * 根据内容id获取留言
 * */
export function listByContentId(contentId: string, params: any) {
  return request({
    url: 'comment/list/' + contentId,
    params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `comment/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: '/comment/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'comment/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'comment/update',
    method: 'post',
    data
  })
}

/**
 * 留言审核回复
 * */
export function audit(data: any) {
  return request({
    url: 'comment/audit',
    method: 'post',
    data
  })
}
