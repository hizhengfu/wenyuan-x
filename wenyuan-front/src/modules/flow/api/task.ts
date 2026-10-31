import request from '@/utils/request'
import type { CompleteTaskRequest, OverdueScope, UrgeTaskRequest } from '../typings/flow'

const base = 'flow/task'

export function getTodoList(params?: any) {
  return request({ url: `${base}/todo`, method: 'get', params })
}

/**
 * 任务中心首页：获取当前用户的待办按业务类型分组聚合
 *
 * 返回 `TodoBusinessTypeGroupVo[]`——每个业务类型一项，含中文名/图标/路由/计数/最新时间。
 * count = 0 的业务类型不返回；按 latestTaskTime 倒序。
 */
export function getTodoGroupByBusinessType() {
  return request({ url: `${base}/todo/group-by-business-type`, method: 'get' })
}

export function getDoneList(params?: any) {
  return request({ url: `${base}/done`, method: 'get', params })
}

export function getCCList(params?: any) {
  return request({ url: `${base}/cc`, method: 'get', params })
}

export function readCC(id: string) {
  return request({ url: `${base}/cc/read/${id}`, method: 'post' })
}

export function readAllCC() {
  return request({ url: `${base}/cc/read-all`, method: 'post' })
}

export function getTaskDetail(id: string) {
  return request({ url: `${base}/${id}`, method: 'get' })
}

export function getInstanceTasks(instanceId: string) {
  return request({ url: `${base}/instance/${instanceId}`, method: 'get' })
}

export function completeTask(data: CompleteTaskRequest) {
  return request({ url: `${base}/complete`, method: 'post', data })
}

export interface BatchApprovalCandidate {
  instanceId: string
  taskId?: string | null
  nodeName?: string | null
  assigneeName?: string | null
  eligible: boolean
  reason?: string | null
  remainingApprovalNodes?: number | null
  mutationVersion?: number | null
}

export interface BatchApprovalResultItem {
  instanceId: string
  taskId?: string | null
  status: 'APPROVED' | 'SKIPPED' | 'FAILED'
  reason?: string | null
}

export interface BatchApprovalResult {
  approved: number
  skipped: number
  failed: number
  items: BatchApprovalResultItem[]
}

export function previewBatchApprove(instanceIds: string[]) {
  return request({ url: `${base}/batch-approve/preview`, method: 'post', data: { instanceIds } })
}

export function batchApproveTasks(items: Array<{ instanceId: string; taskId: string }>, comment?: string) {
  return request({ url: `${base}/batch-approve`, method: 'post', data: { items, comment } })
}

export function previewBatchForceComplete(instanceIds: string[]) {
  return request({ url: `${base}/batch-force-complete/preview`, method: 'post', data: { instanceIds } })
}

export function batchForceComplete(items: Array<{ instanceId: string; mutationVersion: number }>, reason: string) {
  return request({ url: `${base}/batch-force-complete`, method: 'post', data: { items, reason } })
}

// ─── 超时 / 催办 ───────────────────────────────────────────────

/**
 * 查询超时任务列表（dueTime < now 且 PENDING）。
 *
 * @param scope `mine`（默认，仅查我发起的）/ `assigned`（仅查我作为处理人的）/ `all`（仅 ADMIN）
 */
export function getOverdueTasks(params?: {
  scope?: OverdueScope
  businessType?: string
  pageNum?: number
  pageSize?: number
}) {
  return request({ url: `${base}/timeout`, method: 'get', params })
}

/**
 * 触发一次超时扫描（手动）。仅 ADMIN / FLOW_MANAGE 权限可调用。
 *
 * 用途：业务方未启用自动调度时通过运维接口手动触发，返回本次发布的事件数量。
 */
export function scanTimeoutTasks(batchSize: number = 200) {
  return request({ url: `${base}/timeout/scan`, method: 'post', params: { batchSize } })
}

/** 人工催办指定任务，发布 FlowTaskUrgedEvent。 */
export function urgeTask(data: UrgeTaskRequest) {
  return request({ url: `${base}/urge`, method: 'post', data })
}
