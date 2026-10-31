import request from '@/utils/request'

/**
 * 根据节点id获取层级关系
 * */
export function findByNodeId(id: string) {
  return request({
    url: 'knowledge/graph/' + id,
    method: 'get'
  })
}

/**
 * 查询知识图谱关系
 * 参数{nodeId:string,sourceId:string}
 * */
export function graphQuery(params: object) {
  return request({
    url: 'knowledge/graph/query',
    method: 'get',
    params
  })
}
