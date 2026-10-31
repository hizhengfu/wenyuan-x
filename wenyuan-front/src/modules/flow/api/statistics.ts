import request from '@/utils/request'

const base = 'flow/statistics'

export type FlowStatisticsScope = 'mine' | 'global'

/**
 * 全局视角支持的筛选项（个人视角下后端忽略）。
 * 日期格式：yyyy-MM-dd
 */
export interface FlowStatisticsFilters {
  definitionKey?: string
  startDate?: string
  endDate?: string
  initiatorId?: string
}

function buildParams(scope: FlowStatisticsScope, filters?: FlowStatisticsFilters, extra?: Record<string, any>) {
  return {
    scope,
    ...(filters || {}),
    ...(extra || {}),
  }
}

/** 概览数据 */
export function getOverview(scope: FlowStatisticsScope = 'mine', filters?: FlowStatisticsFilters) {
  return request({ url: `${base}/overview`, method: 'get', params: buildParams(scope, filters) })
}

/** 流程实例状态分布 */
export function getInstanceStatus(scope: FlowStatisticsScope = 'mine', filters?: FlowStatisticsFilters) {
  return request({ url: `${base}/instance-status`, method: 'get', params: buildParams(scope, filters) })
}

/** 任务状态分布（仅支持 definitionKey 过滤） */
export function getTaskStatus(scope: FlowStatisticsScope = 'mine', filters?: FlowStatisticsFilters) {
  return request({
    url: `${base}/task-status`,
    method: 'get',
    params: { scope, definitionKey: filters?.definitionKey },
  })
}

/** 流程定义维度 TOP N 排名 */
export function getDefinitionRanking(
  scope: FlowStatisticsScope = 'mine',
  top: number = 10,
  filters?: FlowStatisticsFilters,
) {
  return request({ url: `${base}/definition-ranking`, method: 'get', params: buildParams(scope, filters, { top }) })
}

/** 最近 N 天发起/完成趋势 */
export function getTrend(
  scope: FlowStatisticsScope = 'mine',
  days: number = 30,
  filters?: FlowStatisticsFilters,
) {
  return request({ url: `${base}/trend`, method: 'get', params: buildParams(scope, filters, { days }) })
}
