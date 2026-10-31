import request from '@/utils/request'
import type { FlowOperationNode, JumpFlowRequest, ReactivateFlowRequest, StartFlowRequest } from '../typings/flow'

const base = 'flow/instance'

export function startFlow(data: StartFlowRequest) {
  return request({ url: `${base}/start`, method: 'post', data })
}

export function listInstances(params?: { keyword?: string; status?: string; definitionKey?: string }) {
  return request({ url: `${base}/list`, method: 'get', params })
}

export function myInitiated(params?: any) {
  return request({ url: `${base}/my`, method: 'get', params })
}

export function getInstance(id: string) {
  return request({ url: `${base}/${id}`, method: 'get' })
}

export function getDiagramData(id: string) {
  return request({ url: `${base}/${id}/diagram`, method: 'get' })
}

export function getHistory(id: string) {
  return request({ url: `${base}/${id}/history`, method: 'get' })
}

export function getOperationNodes(id: string) {
  return request<{ data: FlowOperationNode[] }>({ url: `${base}/${id}/operation-nodes`, method: 'get' })
}

export function cancelInstance(id: string) {
  return request({ url: `${base}/cancel/${id}`, method: 'post' })
}

export function reactivateInstance(id: string, data: ReactivateFlowRequest) {
  return request({ url: `${base}/reactivate/${id}`, method: 'post', data })
}

export function jumpInstance(id: string, data: JumpFlowRequest) {
  return request({ url: `${base}/jump/${id}`, method: 'post', data })
}

export function findByBusiness(businessKey: string, businessType?: string) {
  return request({ url: `${base}/business`, method: 'get', params: { businessKey, businessType } })
}
