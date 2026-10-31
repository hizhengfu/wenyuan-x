import { inject, provide, type InjectionKey } from 'vue'
import { search as userSearch, get as userGet } from '@/modules/sys/api/user'
import { tree as roleTreeApi } from '@/modules/sys/api/role'
import { tree as groupTreeApi } from '@/modules/sys/api/group'
import { transformToTreeFormat } from '@/utils'

// ─── 类型定义 ────────────────────────────────────────────────────

export interface AssigneeOption {
  label: string
  value: string
  [key: string]: any
}

export interface AssigneeTreeOption {
  label: string
  value: string
  children?: AssigneeTreeOption[]
  [key: string]: any
}

/**
 * 处理人提供者接口
 * 通过 provide/inject 注入，允许不同业务模块自定义用户/角色/部门的数据来源
 */
export interface AssigneeProvider {
  /** 按关键字搜索用户，用于 FIXED_USER 类型的远程搜索 */
  searchUsers(keyword: string): Promise<AssigneeOption[]>
  /** 按 ID 批量加载用户，用于已选用户的名称回显 */
  loadUsersByIds(ids: string[]): Promise<AssigneeOption[]>
  /** 加载所有角色列表（平铺） */
  loadRoles(): Promise<AssigneeOption[]>
  /** 加载部门树（嵌套结构） */
  loadDepts(): Promise<AssigneeTreeOption[]>
}

export const ASSIGNEE_PROVIDER_KEY: InjectionKey<AssigneeProvider> = Symbol('flowAssigneeProvider')

// ─── 工具函数 ─────────────────────────────────────────────────────

function buildDeptTree(nodes: any[]): AssigneeTreeOption[] {
  return (nodes || []).map((n: any) => ({
    label: n.name,
    value: n.id,
    children: n.children?.length ? buildDeptTree(n.children) : undefined,
  }))
}

// ─── 默认实现：对接 sys 模块 ──────────────────────────────────────

/**
 * 基于 sys 模块 API 的默认 AssigneeProvider 实现
 */
export function createSysAssigneeProvider(): AssigneeProvider {
  return {
    async searchUsers(keyword: string) {
      const res = await userSearch({ keywords: keyword })
      return (res.data || []).map((u: any) => ({
        label: `${u.name}(${u.loginName})`,
        value: u.id,
      }))
    },

    async loadUsersByIds(ids: string[]) {
      if (!ids?.length) return []
      const results = await Promise.allSettled(ids.map((id) => userGet(id)))
      return results
        .filter(
          (r): r is PromiseFulfilledResult<any> =>
            r.status === 'fulfilled' && r.value?.data,
        )
        .map((r) => ({
          label: `${r.value.data.name}(${r.value.data.loginName})`,
          value: r.value.data.id,
        }))
    },

    async loadRoles() {
      const res = await roleTreeApi()
      return (res.data || []).map((r: any) => ({
        label: r.name,
        value: r.id,
      }))
    },

    async loadDepts() {
      // groupTreeApi 已内部 .then(res => res.data)，返回平铺列表
      const flatList = await groupTreeApi({})
      const tree = transformToTreeFormat(flatList || [], { pIdKey: 'parentId' })
      return buildDeptTree(tree)
    },
  }
}

// ─── Composable ────────────────────────────────────────────────────

/**
 * 在子组件中注入 AssigneeProvider
 * 若上层未 provide（如业务详情页直接用 FlowFormDrawer，没有 FlowDesigner 祖先），
 * 走 Vue 的 default 工厂参数回退到 sys 默认实现 —— 避免 Vue 打 "injection not found" 警告
 */
export function useAssigneeProvider(): AssigneeProvider {
  return inject(ASSIGNEE_PROVIDER_KEY, createSysAssigneeProvider, true)
}

/**
 * 在上层组件（如 FlowDesigner）中提供 AssigneeProvider
 * @param provider 自定义实现，不传则使用默认 sys 实现
 */
export function provideAssigneeProvider(provider?: AssigneeProvider) {
  provide(ASSIGNEE_PROVIDER_KEY, provider ?? createSysAssigneeProvider())
}
