import { defineAsyncComponent, type Component } from 'vue'
import { registerFlowForm, hasManualForm, getManualForm } from './formRegistryCore'

/**
 * 业务表单注册表（双模式，全自动）
 *
 * ## 模式一：约定目录自动发现（零配置）
 * 将表单放在 `src/modules/{businessType}/FlowForm.vue`（不区分大小写），无需任何代码。
 *
 * ## 模式二：模块自注册（目录名与 businessType 不一致时）
 * 在业务模块创建 `flowForms.ts` 调用 `registerFlowForm()`。
 * **无需在 main.ts 导入**，本文件通过 eager glob 自动扫描执行所有模块的 flowForms.ts。
 *
 * ## 关于 registerFlowForm 的导入位置
 * flowForms.ts 必须从 `./formRegistryCore` 导入 registerFlowForm，
 * 不能从 `./formRegistry` 导入——否则 Vite 的 eager glob 提升会造成 TDZ。
 */

// 向后兼容：允许从 formRegistry 继续 import registerFlowForm
export { registerFlowForm }

// ─── glob 懒加载：约定目录下的 FlowForm.vue ───────────────────
const formModules = import.meta.glob<{ default: Component }>(
  '/src/modules/*/FlowForm.vue',
)

// ─── 自动扫描执行所有模块的 flowForms.ts ─────────────────────
// eager: true → Vite 会编译为静态 import 并提升到模块顶部，flowForms.ts 在本模块其他
// 语句执行前就跑了。因此 registerFlowForm 的实现必须在一个完全独立的模块
// (formRegistryCore.ts) 里——它无循环依赖，初始化即完成。
import.meta.glob('/src/modules/*/flowForms.ts', { eager: true })

// ─── 查找缓存 ─────────────────────────────────────────────────
const cache = new Map<string, Component | null>()

/**
 * 根据 formKey / businessType 获取对应的业务表单组件
 *
 * 查找优先级（与后端 DESIGN.md §12 一致）：
 * 1. **formKey** 命中（节点级 formKey > 流程级 formKey 在后端已合并解析）
 * 2. **businessType** 命中（约定目录自动发现）
 * 3. 未找到 → 返回 null，调用方降级为通用动态展示
 *
 * @param formKey       任务详情接口返回的 formKey（task.formKey），可空
 * @param businessType  实例的 businessType（业务类型），可空
 */
export function getFlowForm(
  formKey?: string | null,
  businessType?: string | null,
): Component | null {
  // 1) formKey 优先：手动注册或 glob 自动发现都按 formKey 查
  if (formKey && formKey.trim()) {
    const cached = cache.get(`KEY:${formKey}`)
    if (cached !== undefined) return cached
    const comp = lookup(formKey, /* keyMode */ true)
    cache.set(`KEY:${formKey}`, comp)
    if (comp) return comp
    // formKey 显式配置但没找到时打印警告，但仍降级到 businessType
    console.warn(`[FlowForm] formKey "${formKey}" 未匹配到组件，回退 businessType="${businessType ?? '-'}"`)
  }

  // 2) businessType 兜底：原有约定目录扫描
  if (!businessType) return null
  const key = businessType.toUpperCase()
  const cached = cache.get(`BT:${key}`)
  if (cached !== undefined) return cached
  const comp = lookup(businessType, /* keyMode */ false)
  cache.set(`BT:${key}`, comp)
  if (!comp) {
    console.warn(`[FlowForm] 未找到 businessType "${businessType}" 的表单，降级为通用展示`)
  }
  return comp
}

/**
 * 通用查找：手动注册（精确大小写 / 大写）→ glob（多种命名变体）
 * @param identifier  formKey 或 businessType 原始字符串
 */
function lookup(identifier: string, keyMode: boolean): Component | null {
  const upper = identifier.toUpperCase()

  // 手动注册（registerFlowForm 写入）
  if (hasManualForm(upper)) return getManualForm(upper)!
  if (keyMode && hasManualForm(identifier)) return getManualForm(identifier)!

  // 约定目录扫描
  const candidates = [
    `/src/modules/${identifier}/FlowForm.vue`,
    `/src/modules/${upper}/FlowForm.vue`,
    `/src/modules/${upper.toLowerCase()}/FlowForm.vue`,
    `/src/modules/${upper.toLowerCase().replace(/_/g, '-')}/FlowForm.vue`,
  ]
  const matchedKey = candidates.find((k) => formModules[k])
  return matchedKey ? defineAsyncComponent(() => formModules[matchedKey]()) : null
}
