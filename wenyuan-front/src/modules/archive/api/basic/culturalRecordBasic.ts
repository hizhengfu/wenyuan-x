import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'cultural/basic/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `cultural/basic/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'cultural/basic/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'cultural/basic/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'cultural/basic/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'cultural/basic/update',
    method: 'post',
    data
  })
}
export function geoJson(params: object) {
  return request({
    url: 'cultural/basic/geoJson',
    method: 'get',
    params,
  });
}

export function generateKnowledgeGraph(id: string, categoryId?: string) {
  return request({
    url: `cultural/basic/${id}/knowledge-graph`,
    method: 'post',
    params: categoryId ? { categoryId } : undefined,
  });
}

export function previewKnowledgeFilter(filter: object) {
  return request({
    url: 'cultural/basic/knowledge-graph/preview',
    method: 'post',
    data: filter,
  });
}

export function generateKnowledgeBatch(filter: object) {
  return request({
    url: 'cultural/basic/knowledge-graph/batch',
    method: 'post',
    data: filter,
  });
}

