import request from '@/utils/request'
import type { FlowDefinition, FlowModel } from '../typings/flow'

const base = 'flow/definition'

export function listDefinitions(params?: Record<string, any>) {
  return request({ url: `${base}/list`, method: 'get', params })
}

export function getDefinition(id: string) {
  return request({ url: `${base}/${id}`, method: 'get' })
}

export function getDefinitionModel(id: string) {
  return request({ url: `${base}/${id}/model`, method: 'get' })
}

export function getDefinitionNodes(id: string) {
  return request({ url: `${base}/${id}/nodes`, method: 'get' })
}

export function createDefinition(data: FlowDefinition) {
  return request({ url: `${base}/create`, method: 'post', data })
}

export function updateDefinition(data: FlowDefinition) {
  return request({ url: `${base}/update`, method: 'post', data })
}

export function saveDefinitionModel(id: string, modelData: string) {
  return request({ url: `${base}/${id}/model`, method: 'post', data: { modelData } })
}

export function publishDefinition(id: string) {
  return request({ url: `${base}/publish/${id}`, method: 'post' })
}

export function disableDefinition(id: string) {
  return request({ url: `${base}/disable/${id}`, method: 'post' })
}

export function copyDefinition(id: string) {
  return request({ url: `${base}/copy/${id}`, method: 'post' })
}

export function deleteDefinition(id: string) {
  return request({ url: `${base}/delete/${id}`, method: 'post' })
}

/**
 * 查询某流程 key 下所有版本的审计日志（含跨版本）
 *
 * 支持按动作过滤（CREATE/UPDATE_META/SAVE_DRAFT/PUBLISH/DISABLE/COPY/DELETE）。
 */
export function getDefinitionAuditLog(
  key: string,
  params?: { action?: string; pageNum?: number; pageSize?: number },
) {
  return request({ url: `${base}/${key}/audit-log`, method: 'get', params })
}

/** 审计日志：对比两条快照的节点/边差异 */
export function diffDefinitionAuditLog(from: string, to: string) {
  return request({ url: `${base}/audit-log/diff`, method: 'get', params: { from, to } })
}
