import { h, type VNode } from 'vue';
import { NTime, NTooltip } from 'naive-ui';

/**
 * 表格时间列渲染器：
 *  - 单元格用相对时间（"3 分钟前"），紧凑且适合扫一眼
 *  - hover tooltip 显示完整 datetime
 *  - 空值显示 `-`，无效字符串原样回显（不抛错）
 *
 * 用法：
 * ```ts
 * { key: 'createTime', title: '创建时间', width: 180,
 *   render: (row) => renderTimeCell(row.createTime) }
 * ```
 */
export function renderTimeCell(value: string | number | Date | null | undefined): VNode | string {
  if (value === null || value === undefined || value === '') return '-';
  const t = value instanceof Date ? value.getTime() : new Date(value).getTime();
  if (Number.isNaN(t)) return String(value);
  return h(
    NTooltip,
    { trigger: 'hover', placement: 'top' },
    {
      trigger: () => h(NTime, { time: t, type: 'relative' }),
      default: () => h(NTime, { time: t, type: 'datetime' }),
    },
  );
}
