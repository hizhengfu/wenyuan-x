import type { Component } from 'vue';

export const DATAV_SCHEMA_VERSION = 1 as const;

/** 业务对象类型由接入模块自行约定，DataV 核心不维护封闭枚举。 */
export type DatavSubjectType = string;
export type DatavDocumentMode = 'published' | 'draft';

export type DatavRect = {
  x: number;
  y: number;
  w: number;
  h: number;
};

export type DatavWidgetInstance = {
  id: string;
  widgetKey: string;
  widgetVersion: string;
  rect: DatavRect;
  zIndex: number;
  locked: boolean;
  props: Record<string, unknown>;
  dataBinding?: Record<string, unknown>;
};

export type DatavTheme = {
  key: string;
  accent?: string;
};

/**
 * 可持久化的大屏文档协议。平台组件只能通过 widgetKey + widgetVersion 被引用，
 * 不允许后端配置任意 Vue 文件路径或脚本，避免再次演变为不可控的动态插件系统。
 */
export type DatavDocument = {
  schemaVersion: typeof DATAV_SCHEMA_VERSION;
  canvas: {
    width: number;
    height: number;
    columns: number;
    rows: number;
    /** 组件可视区域之间的像素间距，不参与网格坐标和碰撞计算。 */
    gutter: number;
  };
  theme: DatavTheme;
  widgets: DatavWidgetInstance[];
};

export type DatavProject = {
  id: string;
  title: string;
  subjectType?: DatavSubjectType;
  subjectId?: string;
  purpose?: 'MAIN_DASHBOARD';
  version: number;
  needsPublish: boolean;
  document: DatavDocument;
};

export type DatavProjectType = 'PROJECT' | 'TEMPLATE';

export type DatavProjectSummary = {
  id: string;
  title: string;
  projectType: DatavProjectType;
  groupId?: string;
  subjectType?: DatavSubjectType;
  subjectId?: string;
  purpose?: 'MAIN_DASHBOARD';
  version: number;
  publishedVersion?: number;
  needsPublish: boolean;
  widgetCount: number;
  status: 'NORMAL' | 'DELETE' | 'AUDIT';
  updateTime?: string;
  publishTime?: string;
};

export type DatavRevision = {
  id: string;
  version: number;
  revisionType: 'DRAFT' | 'PUBLISHED';
  widgetCount: number;
  createBy?: string;
  createTime: string;
};

/** 组件代码侧清单；尺寸、单例等设计期能力由清单声明，不能由保存的数据越权改变。 */
export type DatavWidgetManifest = {
  key: string;
  version: string;
  title: string;
  category: string;
  component: Component;
  defaultSize: { w: number; h: number };
  minSize: { w: number; h: number };
  singleton?: boolean;
  resizable?: boolean;
};
