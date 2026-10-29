import request from '@/utils/request'

/**
 * 查询节点所有属性
 */
export function listByNode(nodeId: string) {
  return request({
    url: `knowledge/node/attr/list/${nodeId}`,
    method: 'get'
  })
}

/**
 * 保存节点所有属性（整体替换）
 */
export function saveAll(nodeId: string, data: any[]) {
  return request({
    url: `knowledge/node/attr/save/${nodeId}`,
    method: 'post',
    data
  })
}

/**
 * 删除单条属性
 */
export function del(id: string) {
  return request({
    url: `knowledge/node/attr/delete/${id}`,
    method: 'post'
  })
}
