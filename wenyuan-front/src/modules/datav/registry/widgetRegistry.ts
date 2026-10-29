import type { DatavWidgetManifest } from '../core/types';

export class DatavWidgetRegistry {
  private readonly manifests = new Map<string, DatavWidgetManifest>();

  /** 同一个 key 可演进多个版本，但同一 key@version 只能由一份受控代码注册。 */
  register(manifest: DatavWidgetManifest) {
    const identity = `${manifest.key}@${manifest.version}`;
    if (this.manifests.has(identity)) throw new Error(`DataV 组件重复注册：${identity}`);
    this.manifests.set(identity, manifest);
    return this;
  }

  resolve(key: string, version = '1.0.0') {
    return this.manifests.get(`${key}@${version}`);
  }

  list() {
    return [...this.manifests.values()].sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'));
  }
}
