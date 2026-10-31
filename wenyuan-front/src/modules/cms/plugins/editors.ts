import type { Component } from 'vue';
export interface CmsEditor { key: string; component: Component }
// 各插件提供自己的编辑器清单，中心入口通过后端 editorKey 查找，不再维护内容类型分支。
const manifests = import.meta.glob<{ default: CmsEditor }>('/src/modules/*/cms-plugins/*.ts', { eager: true });
const platformManifests = import.meta.glob<{ default: CmsEditor }>('/src/platforms/*/cms-plugins/*.ts', { eager: true });
const editors = new Map<string, Component>();
for (const [path, entry] of Object.entries({ ...manifests, ...platformManifests })) {
  // 模块编辑器通用，平台专属清单只加载当前 APP_TARGET，防止其他平台的同名清单混入。
  if (path.startsWith('/src/platforms/') && path.split('/')[3] !== APP_TARGET) continue;
  const { key, component } = entry.default;
  // 冲突显式报错，不按扫描顺序覆盖；清单组件可以使用异步组件延迟加载表单。
  if (!key || editors.has(key)) throw new Error(`重复或无效的 CMS 编辑器标识: ${key}`);
  editors.set(key, component);
}
export function cmsEditor(key: string): Component | undefined { return editors.get(key); }
