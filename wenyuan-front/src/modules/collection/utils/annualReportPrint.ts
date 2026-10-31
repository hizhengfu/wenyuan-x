import { annualCountFields, annualQuantityFields } from './annualReportFields';

type AnnualPrintData = {
  year: number;
  completeYear: boolean;
  cutoff?: unknown;
  issues?: Array<{ totalRegNo?: string; description?: string }>;
  quantityBalances?: Array<Record<string, unknown>>;
  quantities?: Array<Record<string, unknown>>;
  changesDescription?: string;
  [key: string]: unknown;
};

function escapeHtml(value: unknown): string {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[char]!);
}

/** Print data directly so pagination controls and current-page limits never enter the report. */
export function buildAnnualReportPrintHtml(data: AnnualPrintData, cutoffText: string): string {
  const quantities = data.quantityBalances || data.quantities || [];
  const issues = data.issues || [];
  const countRows = annualCountFields.map((field) =>
    `<tr><th>${escapeHtml(field.label)}</th><td>${escapeHtml(data[field.key] ?? '未填写')}</td></tr>`,
  ).join('');
  const quantityHeaders = [{ key: 'unit', label: '计量单位' }, ...annualQuantityFields];
  const quantityRows = quantities.map((row) => `<tr>${quantityHeaders.map((field) =>
    `<td>${escapeHtml(row[field.key] ?? '未填写')}</td>`,
  ).join('')}</tr>`).join('');
  const issueRows = issues.map((row) =>
    `<tr><td>${escapeHtml(row.totalRegNo)}</td><td>${escapeHtml(row.description)}</td></tr>`,
  ).join('');
  return `<section class="annual-report-print">
    <h2>${escapeHtml(data.year)} 年度正式总账计算预览${data.completeYear ? '' : '（年度未结束）'}</h2>
    <p>统计截至 ${escapeHtml(cutoffText)}；范围：当前收藏单位；仅包含具有完整本馆登记历史的记录。</p>
    <p>本表为系统计算预览，打印不会产生签署、报送或备案事实。</p>
    ${issues.length ? `<p>历史核对缺口 ${issues.length} 条，下列计算尚不能代表完整年度报送数。须核对原纸质总账及逐件明细。</p>` : ''}
    <h3>条目统计</h3>
    <table><thead><tr><th>统计项目</th><th>条目数</th></tr></thead><tbody>${countRows}</tbody></table>
    <h3>按计量单位统计数量</h3>
    <table><thead><tr>${quantityHeaders.map((field) => `<th>${escapeHtml(field.label)}</th>`).join('')}</tr></thead>
      <tbody>${quantityRows || `<tr><td colspan="${quantityHeaders.length}">暂无数量统计</td></tr>`}</tbody></table>
    ${data.changesDescription ? `<p>增减及变更说明：${escapeHtml(data.changesDescription)}</p>` : ''}
    ${issues.length ? `<h3>待核对事项（共 ${issues.length} 条）</h3><table class="annual-report-issues"><thead><tr><th>登记号</th><th>待核对事项</th></tr></thead><tbody>${issueRows}</tbody></table>` : ''}
  </section>`;
}

export const annualReportPrintStyles = `
@page { size: A4 landscape; margin: 16mm 12mm; }
html, body { overflow: visible; page-break-inside: auto; break-inside: auto; }
.annual-report-print { color: #000; font-size: 10pt; line-height: 1.5; }
.annual-report-print h2 { text-align: center; font-size: 16pt; }
.annual-report-print h3 { font-size: 12pt; margin: 16px 0 8px; break-after: avoid; page-break-after: avoid; }
.annual-report-print table { width: 100%; border-collapse: collapse; table-layout: fixed; break-inside: auto; page-break-inside: auto; page-break-after: auto; }
.annual-report-print thead { display: table-header-group; }
.annual-report-print tr { break-inside: avoid; page-break-inside: avoid; page-break-after: auto; }
.annual-report-print th, .annual-report-print td { border: 1px solid #555; padding: 6px; min-width: 0; overflow-wrap: anywhere; }
.annual-report-print td { page-break-after: auto; }
.annual-report-issues th:first-child { width: 20%; }
`;
