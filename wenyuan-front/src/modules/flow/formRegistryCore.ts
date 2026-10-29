import type { Component } from 'vue'

/**
 * 手动注册表底层存储（独立模块）
 *
 * 放在单独文件是为了规避 Vite 的 eager glob 提升问题：
 * `import.meta.glob('...', { eager: true })` 会被编译为静态 import 提升到模块顶部，
 * 若 Map 与 eager glob 写在同一文件，Map 会因 TDZ 尚未初始化而崩溃。
 */
const manualRegistry = new Map<string, Component>()

export function registerFlowForm(businessType: string, component: Component): void {
  manualRegistry.set(businessType.toUpperCase(), component)
}

export function hasManualForm(key: string): boolean {
  return manualRegistry.has(key)
}

export function getManualForm(key: string): Component | undefined {
  return manualRegistry.get(key)
}
