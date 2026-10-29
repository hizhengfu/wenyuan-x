import request from '@/utils/request'

const baseUrl = 'cultural/basic/group/manage'

export function list(params: Record<string, any>) {
  return request({ url: `${baseUrl}/list`, method: 'get', params })
}

export function get(id: string) {
  return request({ url: `${baseUrl}/${id}`, method: 'get' })
}

export function add(data: Record<string, any>) {
  return request({ url: `${baseUrl}/create`, method: 'post', data })
}

export function edit(data: Record<string, any>) {
  return request({ url: `${baseUrl}/update`, method: 'post', data })
}

export function del(id: string) {
  return request({ url: `${baseUrl}/delete/${id}`, method: 'post' })
}

export function relationContext(groupId: string) {
  return request({ url: `${baseUrl}/${groupId}/relation-context`, method: 'get' })
}

export function relationCandidates(groupId: string, params: Record<string, any>) {
  return request({ url: `${baseUrl}/${groupId}/relation-candidates`, method: 'get', params })
}

export function addRelations(groupId: string, basicIds: string[]) {
  return request({ url: `${baseUrl}/${groupId}/relations`, method: 'post', data: { basicIds } })
}

export function relations(groupId: string, params: Record<string, any>) {
  return request({ url: `${baseUrl}/${groupId}/relations`, method: 'get', params })
}

export function deleteRelation(expectedGroupId: string, basicId: string) {
  return request({ url: `${baseUrl}/${expectedGroupId}/relations/delete/${basicId}`, method: 'post' })
}
