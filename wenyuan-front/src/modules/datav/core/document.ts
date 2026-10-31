import {
  DATAV_SCHEMA_VERSION,
  type DatavDocument,
  type DatavTheme,
  type DatavWidgetInstance,
} from './types';

export function createDatavDocument(widgets: DatavWidgetInstance[] = [], theme: DatavTheme = { key: 'theme1' }): DatavDocument {
  return {
    schemaVersion: DATAV_SCHEMA_VERSION,
    canvas: { width: 1920, height: 1080, columns: 24, rows: 66, gutter: 6 },
    theme: { ...theme },
    widgets: widgets.map(cloneDatavWidget),
  };
}

export function cloneDatavWidget(widget: DatavWidgetInstance): DatavWidgetInstance {
  return {
    ...widget,
    rect: { ...widget.rect },
    props: { ...widget.props },
    dataBinding: widget.dataBinding ? { ...widget.dataBinding } : undefined,
  };
}

export function cloneDatavDocument(document: DatavDocument): DatavDocument {
  return {
    ...document,
    canvas: { ...document.canvas },
    theme: { ...document.theme },
    widgets: document.widgets.map(cloneDatavWidget),
  };
}

/**
 * 为同一 schemaVersion 内新增的可选字段补默认值。
 * 这不是旧版布局迁移；结构或 schemaVersion 不匹配时仍会被后续校验拒绝。
 */
export function normalizeDatavDocument(value: unknown): unknown {
  if (!value || typeof value !== 'object') return value;
  const document = value as Partial<DatavDocument>;
  if (!document.canvas || typeof document.canvas !== 'object') return value;
  return {
    ...document,
    canvas: {
      ...document.canvas,
      gutter:
        typeof document.canvas.gutter === 'number' && Number.isFinite(document.canvas.gutter)
          ? document.canvas.gutter
          : 6,
    },
  };
}

export function validateDatavDocument(value: unknown): value is DatavDocument {
  if (!value || typeof value !== 'object') return false;
  const document = value as Partial<DatavDocument>;
  if (document.schemaVersion !== DATAV_SCHEMA_VERSION || !document.canvas || !document.theme) return false;
  if (
    typeof document.canvas.gutter !== 'number' ||
    !Number.isFinite(document.canvas.gutter) ||
    document.canvas.gutter < 0 ||
    document.canvas.gutter > 32
  ) return false;
  if (!Array.isArray(document.widgets) || typeof document.theme.key !== 'string') return false;
  const ids = new Set<string>();
  return document.widgets.every((widget) => {
    if (!widget || typeof widget !== 'object' || typeof widget.id !== 'string' || ids.has(widget.id)) return false;
    ids.add(widget.id);
    const rect = widget.rect;
    return (
      typeof widget.widgetKey === 'string' &&
      typeof widget.widgetVersion === 'string' &&
      typeof widget.zIndex === 'number' &&
      typeof widget.locked === 'boolean' &&
      !!rect &&
      [rect.x, rect.y, rect.w, rect.h].every((number) => typeof number === 'number' && Number.isFinite(number)) &&
      rect.w > 0 &&
      rect.h > 0 &&
      rect.x >= 0 &&
      rect.y >= 0 &&
      rect.x + rect.w <= document.canvas!.columns &&
      rect.y + rect.h <= document.canvas!.rows
    );
  });
}
