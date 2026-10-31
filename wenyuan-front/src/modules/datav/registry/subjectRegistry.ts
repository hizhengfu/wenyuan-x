import type { Component } from 'vue';
import type { RouteLocationRaw } from 'vue-router';
import type { DatavSubjectType } from '../core/types';

/**
 * 业务模块接入 DataV 的扩展槽。
 * selector 可替换默认 ID 输入框；路由构造函数用于把通用管理页连接到业务设计页和展示页。
 */
export type DatavSubjectAdapter = {
  type: DatavSubjectType;
  label: string;
  idLabel?: string;
  idPlaceholder?: string;
  selector?: Component;
  editRoute?: (subjectId: string) => RouteLocationRaw;
  previewRoute?: (subjectId: string) => RouteLocationRaw;
};

class DatavSubjectRegistry {
  private readonly adapters = new Map<DatavSubjectType, DatavSubjectAdapter>();

  register(adapter: DatavSubjectAdapter) {
    if (!adapter.type.trim()) throw new Error('DataV 业务类型不能为空');
    if (this.adapters.has(adapter.type)) throw new Error(`DataV 业务适配器重复注册：${adapter.type}`);
    this.adapters.set(adapter.type, adapter);
  }

  resolve(type?: DatavSubjectType) {
    return type ? this.adapters.get(type) : undefined;
  }

  list() {
    return [...this.adapters.values()];
  }
}

export const datavSubjectRegistry = new DatavSubjectRegistry();
export const registerDatavSubjectAdapter = (adapter: DatavSubjectAdapter) => datavSubjectRegistry.register(adapter);
