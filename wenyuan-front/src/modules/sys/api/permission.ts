import request from '@/utils/request'
import {transformToTreeFormat} from '@/utils'

export type PermissionEndpointMappingStatus =
  | 'EXACT'
  | 'AUTHENTICATED_ONLY'
  | 'DYNAMIC_POLICY'
  | 'NO_METHOD_POLICY'
  | 'UNSUPPORTED'

export interface PermissionApiEndpoint {
  key: string
  httpMethod: string
  path: string
  controller: string
  handler: string
  summary: string
  rawExpression?: string | null
  policySource: 'METHOD' | 'TYPE' | 'NONE'
  mappingStatus: PermissionEndpointMappingStatus
  authorityCodes: string[]
  roleCodes: string[]
}

export interface PermissionCatalogModule {
  code: string
  label: string
  order: number
  permissionCount: number
  usedPermissionCount: number
}

export interface PermissionCatalogItem {
  id: string
  applicationId: string | null
  parentId?: string | null
  parentIds?: string | null
  name: string
  code: string
  moduleCode: string
  moduleLabel: string
  isSys: boolean
  source?: string | null
  used: boolean
  globalBypass: boolean
  applicationAmbiguous: boolean
  applicationIds: string[]
  endpointCount: number
  endpoints: PermissionApiEndpoint[]
}

export interface PermissionCatalogSnapshot {
  modules: PermissionCatalogModule[]
  permissions: PermissionCatalogItem[]
  unmappedEndpoints: PermissionApiEndpoint[]
  diagnostics: {
    codeNotDeclared: Array<{
      code: string
      endpointCount: number
      endpoints: PermissionApiEndpoint[]
    }>
    multiApplicationCodes: Array<{
      code: string
      applicationIds: string[]
    }>
  }
  totals: {
    permissionCount: number
    usedPermissionCount: number
    endpointCount: number
    unmappedEndpointCount: number
  }
}

export interface PermissionCatalogQuery {
  applicationId: string
  moduleCode?: string
  q?: string
  mappingStatus?: PermissionEndpointMappingStatus | PermissionEndpointMappingStatus[]
  used?: boolean
}

export function list(params: object) {
  return request({
    url: 'permisson/list',
    params: params,
    method: 'get'
  })
}

export function tree(params: object, filter: boolean = false) {
  return request({
    url: 'permisson/tree',
    params: params,
    method: 'get'
  }).then((res) => {
    let data = res.data || []
    if (filter) {
      data = data.filter((item: any) => {
        return item.parentId === '0'
      })
    }
    return data
  })
}

export function catalog(params: PermissionCatalogQuery) {
  return request({
    url: 'permission-catalog',
    params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `permisson/${id}`,
    method: 'get'
  })
}

export function add(data: object) {
  return request({
    url: 'permisson/create',
    method: 'post',
    data
  })
}

export function del(id: string) {
  return request({
    url: 'permisson/delete/' + id,
    method: 'post'
  })
}

export function edit(data: object) {
  return request({
    url: 'permisson/update',
    method: 'post',
    data
  })
}
