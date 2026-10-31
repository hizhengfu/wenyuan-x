import dayjs from 'dayjs';
import type { CollectionVo } from '../typings/collection';

export type PrintDictOption = { label: string; value: string };
export type PrintDictCache = Record<string, PrintDictOption[]>;
export type PrintQuerySummaryItem = {
  label: string;
  value?: unknown;
  dictType?: string | null;
};
export type PrintCollectionListOptions = {
  title?: string;
  orgName?: string | null;
  generatedAt?: unknown;
  querySummary?: PrintQuerySummaryItem[];
};
export type PrintCollectionFieldSpec = {
  label: string;
  key?: keyof CollectionVo;
  dictType?: string;
  width?: number;
  minWidth?: number;
  value?: (row: CollectionVo) => unknown;
};

export const printCollectionFieldSpecs: PrintCollectionFieldSpec[] = [
  { label: '总登记号', key: 'totalRegNo', width: 120 },
  { label: '名称', key: 'name', minWidth: 150 },
  { label: '级别', key: 'level', dictType: 'col_relic_level', width: 86 },
  { label: '分类', value: (row) => row.categoryName || row.categoryId, minWidth: 120 },
  { label: '年代', key: 'era', width: 100 },
  { label: '质地', key: 'texture', dictType: 'col_texture', width: 86 },
  {
    label: '数量',
    width: 74,
    value: (row) => [row.quantity, row.unit].filter((item) => item !== undefined && item !== null && String(item).trim() !== '').join(' '),
  },
  { label: '完残', key: 'completeness', dictType: 'col_completeness', width: 86 },
  { label: '库位', key: 'warehouseName', minWidth: 120 },
  {
    label: '影像/附件',
    width: 86,
    value: (row) => `${Number(row.mediaCount || 0)} / ${Number(row.attachmentCount || 0)}`,
  },
];

function escapeHtml(value: unknown) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function isPresent(value: unknown) {
  return value !== undefined && value !== null && String(value).trim() !== '';
}

function cleanDateTimeText(value: string) {
  return value.replace('T', ' ').replace(/\.\d+(?=(Z|[+-]\d{2}:?\d{2})?$)/, '').replace(/Z$/, '');
}

function normalizeTimestamp(value: number) {
  if (!Number.isFinite(value)) return null;
  if (Math.abs(value) < 1_000_000_000) return null;
  return Math.abs(value) < 10_000_000_000 ? value * 1000 : value;
}

function isDateTimeText(value: string) {
  return /^\d{4}[-/]\d{1,2}[-/]\d{1,2}[ T]\d{1,2}:\d{1,2}/.test(value);
}

export function formatPrintDateTime(value: unknown) {
  if (value === undefined || value === null || value === '') return '';
  if (value instanceof Date) {
    const date = dayjs(value);
    return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : '';
  }
  if (typeof value === 'number') {
    const timestamp = normalizeTimestamp(value);
    if (timestamp === null) return String(value);
    const date = dayjs(timestamp);
    return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : String(value);
  }

  const text = String(value).trim();
  if (!text) return '';
  if (/^-?\d+$/.test(text)) {
    const timestamp = normalizeTimestamp(Number(text));
    if (timestamp !== null) {
      const date = dayjs(timestamp);
      return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : text;
    }
    return text;
  }
  if (isDateTimeText(text)) {
    const date = dayjs(text);
    return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : cleanDateTimeText(text);
  }
  return cleanDateTimeText(text);
}

function normalizeGeneratedAt(value: unknown) {
  return formatPrintDateTime(value) || formatPrintDateTime(new Date());
}

export function printDictLabel(dicts: PrintDictCache, type: string | null | undefined, value: unknown) {
  const text = String(value ?? '').trim();
  if (!text) return '';
  if (!type) return text;
  const options = dicts[type] || [];
  return text
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => options.find((option) => option.value == item)?.label || item)
    .join('，');
}

export function formatPrintCollectionValue(row: CollectionVo, spec: PrintCollectionFieldSpec, dicts: PrintDictCache = {}) {
  const raw = spec.value ? spec.value(row) : spec.key ? row[spec.key] : '';
  return printDictLabel(dicts, spec.dictType, raw) || '';
}

export function buildPrintQuerySummary(querySummary: PrintQuerySummaryItem[] = [], dicts: PrintDictCache = {}) {
  const items = querySummary
    .map((item) => {
      const value = printDictLabel(dicts, item.dictType, item.value);
      return value ? `${item.label}：${value}` : '';
    })
    .filter(Boolean);
  return items.length ? items.join('；') : '全部';
}

export function buildCollectionListPrintHtml(rows: CollectionVo[], dicts: PrintDictCache = {}, options: PrintCollectionListOptions = {}) {
  const title = options.title || '藏品清册';
  const orgName = options.orgName || '全部收藏单位';
  const generatedAt = normalizeGeneratedAt(options.generatedAt);
  const summary = buildPrintQuerySummary(options.querySummary, dicts);
  const body = rows.length
    ? rows
        .map(
          (row, index) => `
            <tr>
              <td>${index + 1}</td>
              ${printCollectionFieldSpecs.map((spec) => `<td>${escapeHtml(formatPrintCollectionValue(row, spec, dicts))}</td>`).join('')}
            </tr>
          `,
        )
        .join('')
    : `<tr><td colspan="${printCollectionFieldSpecs.length + 1}" class="empty">当前筛选暂无藏品</td></tr>`;

  return `
    <section class="collection-list-print">
      <h1>${escapeHtml(title)}</h1>
      <div class="print-meta">
        <span>收藏单位：${escapeHtml(orgName)}</span>
        <span>生成时间：${escapeHtml(generatedAt)}</span>
        <span>共 ${rows.length} 件</span>
      </div>
      <div class="print-query">筛选条件：${escapeHtml(summary)}</div>
      <table>
        <thead>
          <tr>
            <th>序号</th>
            ${printCollectionFieldSpecs.map((spec) => `<th>${escapeHtml(spec.label)}</th>`).join('')}
          </tr>
        </thead>
        <tbody>${body}</tbody>
      </table>
      <div class="signatures">
        <span>制表人：</span>
        <span>保管员：</span>
        <span>复核人：</span>
        <span>日期：</span>
      </div>
      <div class="print-note">本清册按当前检索条件生成，用于账物核对、移交、盘点和纸质归档。</div>
    </section>
  `;
}

export const collectionListPrintStyles = `
  @page { size: A4 landscape; margin: 12mm; }
  .collection-list-print { font-family: SimSun, "Songti SC", serif; color: #111; padding: 6px; }
  .collection-list-print h1 { text-align: center; font-size: 24px; letter-spacing: 0; margin: 0 0 10px; }
  .collection-list-print .print-meta { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px 24px; font-size: 12px; margin-bottom: 6px; }
  .collection-list-print .print-query { font-size: 12px; line-height: 1.5; margin: 0 0 10px; }
  .collection-list-print table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  .collection-list-print th,
  .collection-list-print td { border: 1px solid #222; padding: 5px 5px; font-size: 11px; line-height: 1.35; word-break: break-word; vertical-align: middle; }
  .collection-list-print th { background: #f6f6f6; font-weight: 700; text-align: center; }
  .collection-list-print th:first-child,
  .collection-list-print td:first-child { width: 42px; text-align: center; }
  .collection-list-print .empty { text-align: center; color: #666; padding: 16px; }
  .collection-list-print .signatures { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; margin-top: 22px; font-size: 13px; }
  .collection-list-print .signatures span { min-height: 30px; border-bottom: 1px solid #222; }
  .collection-list-print .print-note { margin-top: 12px; font-size: 11px; color: #444; }
`;
