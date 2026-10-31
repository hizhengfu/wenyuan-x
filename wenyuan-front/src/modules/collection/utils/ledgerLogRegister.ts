import { formatPrintDateTime } from './printCollectionList';

export type LedgerLogRegisterRow = {
  totalRegNo?: string | null;
  collectionName?: string | null;
  collectionId?: string | null;
  action?: string | null;
  field?: string | null;
  before?: string | null;
  after?: string | null;
  reason?: string | null;
  refBizId?: string | null;
  createBy?: string | null;
  createByName?: string | null;
  createTime?: string | null;
};

export type LedgerLogRegisterFilters = {
  keyword?: string | null;
  totalRegNo?: string | null;
  refBizId?: string | null;
  action?: string | null;
  field?: string | null;
  createTimeStart?: number | null;
  createTimeEnd?: number | null;
};

export type LedgerLogActionLabels = Record<string, string>;

const columns: { title: string; key: keyof LedgerLogRegisterRow; action?: boolean; time?: boolean }[] = [
  { title: '总登记号', key: 'totalRegNo' },
  { title: '藏品名称', key: 'collectionName' },
  { title: '藏品ID', key: 'collectionId' },
  { title: '关联业务单ID', key: 'refBizId' },
  { title: '动作', key: 'action', action: true },
  { title: '字段', key: 'field' },
  { title: '原值', key: 'before' },
  { title: '新值', key: 'after' },
  { title: '原因', key: 'reason' },
  { title: '记录人', key: 'createBy' },
  { title: '记录时间', key: 'createTime', time: true },
];

function escapeHtml(value: unknown) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function escapeCsv(value: unknown) {
  const text = String(value ?? '');
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function formatLedgerLogDateTime(value: unknown) {
  return formatPrintDateTime(value);
}

export function ledgerLogActionText(value: unknown, actionLabels: LedgerLogActionLabels = {}) {
  const key = String(value ?? '').trim();
  return key ? actionLabels[key] || key : '';
}

function cellText(row: LedgerLogRegisterRow, column: (typeof columns)[number], actionLabels: LedgerLogActionLabels) {
  const raw = row[column.key];
  if (column.action) return ledgerLogActionText(raw, actionLabels);
  if (column.time) return formatLedgerLogDateTime(raw);
  if (column.key === 'createBy') return row.createByName || row.createBy || '';
  return String(raw ?? '');
}

export function buildLedgerLogCsv(rows: readonly LedgerLogRegisterRow[], actionLabels: LedgerLogActionLabels = {}) {
  const header = columns.map((column) => column.title).join(',');
  const body = rows.map((row) => columns.map((column) => escapeCsv(cellText(row, column, actionLabels))).join(','));
  return [header, ...body].join('\n');
}

function filterSummary(filters: LedgerLogRegisterFilters) {
  const parts = [
    filters.totalRegNo ? `总登记号：${filters.totalRegNo}` : '',
    filters.refBizId ? `关联业务单ID：${filters.refBizId}` : '',
    filters.action ? `动作：${filters.action}` : '',
    filters.field ? `字段：${filters.field}` : '',
    filters.keyword ? `关键字：${filters.keyword}` : '',
    filters.createTimeStart || filters.createTimeEnd
      ? `记录时间：${formatLedgerLogDateTime(filters.createTimeStart) || '不限'} 至 ${formatLedgerLogDateTime(filters.createTimeEnd) || '不限'}`
      : '',
  ].filter(Boolean);
  return parts.length ? parts.join('　') : '全部资料登记账记录';
}

export function buildLedgerLogRegisterHtml(
  rows: readonly LedgerLogRegisterRow[],
  actionLabels: LedgerLogActionLabels = {},
  filters: LedgerLogRegisterFilters = {},
) {
  const body = rows.length
    ? rows
        .map(
          (row, index) => `
            <tr>
              <td>${index + 1}</td>
              ${columns.map((column) => `<td>${escapeHtml(cellText(row, column, actionLabels)) || '&nbsp;'}</td>`).join('')}
            </tr>
          `,
        )
        .join('')
    : `<tr><td colspan="${columns.length + 1}" class="empty">暂无登记账记录</td></tr>`;
  return `
    <style>
      body { font-family: "Microsoft YaHei", Arial, sans-serif; color: #111827; }
      .ledger-register { padding: 18px 20px; }
      h1 { margin: 0 0 8px; text-align: center; font-size: 22px; letter-spacing: 0; }
      .meta { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; font-size: 12px; color: #4b5563; }
      table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 11px; }
      th, td { border: 1px solid #111827; padding: 5px 4px; vertical-align: top; word-break: break-word; }
      th { background: #f3f4f6; font-weight: 700; text-align: center; }
      td:first-child { text-align: center; }
      .empty { height: 56px; text-align: center; color: #6b7280; }
      .signatures { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; margin-top: 18px; font-size: 12px; }
      @media print { .ledger-register { padding: 0; } }
    </style>
    <div class="ledger-register">
      <h1>藏品资料登记账</h1>
      <div class="meta">
        <span>${escapeHtml(filterSummary(filters))}</span>
        <span>共 ${rows.length} 条</span>
      </div>
      <table>
        <thead>
          <tr>
            <th style="width: 34px">序号</th>
            ${columns.map((column) => `<th>${escapeHtml(column.title)}</th>`).join('')}
          </tr>
        </thead>
        <tbody>${body}</tbody>
      </table>
      <div class="signatures">
        <span>制表：</span>
        <span>复核：</span>
        <span>负责人：</span>
        <span>日期：</span>
      </div>
    </div>
  `;
}
