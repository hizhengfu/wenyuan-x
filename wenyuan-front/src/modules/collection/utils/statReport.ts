export interface CollectionStatSummary {
  total: number;
  pendingRegistrationTotal?: number;
  unverifiedRegistrationTotal?: number;
  retiredTotal?: number;
  byLevel: Record<string, number>;
  byStatus: Record<string, number>;
  byCatalogStatus: Record<string, number>;
  byCategory: Record<string, number>;
  byEra: Record<string, number>;
  bySource: Record<string, number>;
  byTexture: Record<string, number>;
  byCompleteness: Record<string, number>;
  byPreserveState: Record<string, number>;
  byWarehouse: Record<string, number>;
  byOrg: Record<string, number>;
  imageCount: number;
  attachmentCount: number;
  imageRate: number;
  attachmentRate: number;
  registeredThisYear: number;
  registeredThisMonth: number;
}

export interface StatOverviewItem {
  label: string;
  value: number | string;
  suffix?: string;
}

export interface StatDistributionSection {
  key: keyof Pick<
    CollectionStatSummary,
    | 'byLevel'
    | 'byStatus'
    | 'byCatalogStatus'
    | 'byCategory'
    | 'byEra'
    | 'bySource'
    | 'byTexture'
    | 'byCompleteness'
    | 'byPreserveState'
    | 'byWarehouse'
    | 'byOrg'
  >;
  title: string;
  dictType?: string;
}

export interface StatReportOptions {
  title?: string;
  scopeLabel?: string;
}

export interface StatDistributionRow {
  section: string;
  name: string;
  rawName: string;
  value: number;
  rate: number;
  dictType?: string;
}

export type StatLabelResolver = (dictType: string | undefined, value: string) => string;

export const statDistributionSections: StatDistributionSection[] = [
  { key: 'byLevel', title: '文物级别（仅文物藏品）', dictType: 'col_relic_level' },
  { key: 'byStatus', title: '按状态', dictType: 'col_life_status' },
  { key: 'byCatalogStatus', title: '按编目', dictType: 'col_catalog_status' },
  { key: 'byCategory', title: '按分类' },
  { key: 'byEra', title: '按年代' },
  { key: 'bySource', title: '按来源', dictType: 'col_source' },
  { key: 'byTexture', title: '按质地', dictType: 'col_texture' },
  { key: 'byCompleteness', title: '按完残', dictType: 'col_completeness' },
  { key: 'byPreserveState', title: '按保存状态', dictType: 'col_preserve' },
  { key: 'byWarehouse', title: '按库位' },
  { key: 'byOrg', title: '按收藏单位' },
];

function escapeHtml(value: unknown) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function percent(value: number, total: number) {
  if (!total) return 0;
  return Math.round((value / total) * 100);
}

function normalizeName(value: string) {
  const text = String(value ?? '').trim();
  return text || '未设置';
}

function resolveLabel(section: StatDistributionSection, rawName: string, resolver?: StatLabelResolver) {
  const normalized = normalizeName(rawName);
  if (normalized === '未设置') return normalized;
  return resolver?.(section.dictType, normalized) || normalized;
}

export function buildStatOverviewItems(stat: CollectionStatSummary): StatOverviewItem[] {
  return [
    { label: '总登记账记录数', value: stat.total },
    { label: '本年登记（现有记录）', value: stat.registeredThisYear },
    { label: '本月登记（现有记录）', value: stat.registeredThisMonth },
    { label: '图像率', value: `${stat.imageRate}%`, suffix: `${stat.imageCount} 条记录` },
    { label: '资料附件率', value: `${stat.attachmentRate}%`, suffix: `${stat.attachmentCount} 条记录` },
  ];
}

export function buildStatDistributionRows(
  stat: CollectionStatSummary,
  resolver?: StatLabelResolver,
): StatDistributionRow[] {
  return statDistributionSections.flatMap((section) => {
    const source = stat[section.key] || {};
    return Object.entries(source)
      .map(([rawName, value]) => ({
        section: section.title,
        name: resolveLabel(section, rawName, resolver),
        rawName: normalizeName(rawName),
        value,
        rate: percent(
          value,
          section.key === 'byLevel' ? Object.values(source).reduce((sum, n) => sum + n, 0) : stat.total,
        ),
        dictType: section.dictType,
      }))
      .sort((a, b) => b.value - a.value || a.name.localeCompare(b.name, 'zh-Hans-CN'));
  });
}

export function buildStatReportHtml(
  stat: CollectionStatSummary,
  resolver?: StatLabelResolver,
  options: StatReportOptions = {},
) {
  const title = options.title || '藏品统计分析报表';
  const overviewRows = buildStatOverviewItems(stat)
    .map(
      (item) => `
        <tr>
          <th>${escapeHtml(item.label)}</th>
          <td>${escapeHtml(item.value)}${item.suffix ? `<span>${escapeHtml(item.suffix)}</span>` : ''}</td>
        </tr>
      `,
    )
    .join('');
  const distributionRows = buildStatDistributionRows(stat, resolver)
    .map(
      (row) => `
        <tr>
          <td>${escapeHtml(row.section)}</td>
          <td>${escapeHtml(row.name)}</td>
          <td>${escapeHtml(row.value)}</td>
          <td>${escapeHtml(`${row.rate}%`)}</td>
        </tr>
      `,
    )
    .join('');
  return `
    <section class="collection-stat-print">
      <h1>${escapeHtml(title)}</h1>
      ${options.scopeLabel ? `<p class="scope">统计口径：${escapeHtml(options.scopeLabel)}</p>` : ''}
      <table class="overview">
        <tbody>${overviewRows}</tbody>
      </table>
      <h2>分布明细</h2>
      <table class="distribution">
        <thead>
          <tr><th>统计口径</th><th>项目</th><th>记录数</th><th>占比</th></tr>
        </thead>
        <tbody>${distributionRows || '<tr><td colspan="4" class="empty">暂无分布数据</td></tr>'}</tbody>
      </table>
      <div class="signatures">
        <span>制表人：</span><span>复核人：</span><span>日期：</span>
      </div>
    </section>
  `;
}

export const statReportPrintStyle = `
  @page { size: A4 portrait; margin: 14mm; }
  .collection-stat-print { font-family: SimSun, "Songti SC", serif; color: #111; padding: 8px; }
  .collection-stat-print h1 { text-align: center; font-size: 24px; letter-spacing: 0; margin: 0 0 14px; }
  .collection-stat-print .scope { text-align: center; margin: -6px 0 12px; font-size: 13px; }
  .collection-stat-print h2 { font-size: 15px; margin: 18px 0 8px; }
  .collection-stat-print table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  .collection-stat-print th, .collection-stat-print td { border: 1px solid #222; padding: 7px 9px; font-size: 12px; line-height: 1.5; word-break: break-word; }
  .collection-stat-print th { background: #f6f6f6; text-align: center; }
  .collection-stat-print .overview th { width: 25%; }
  .collection-stat-print .overview td span { margin-left: 8px; color: #555; }
  .collection-stat-print .distribution th:first-child, .collection-stat-print .distribution td:first-child { width: 24%; }
  .collection-stat-print .distribution th:nth-child(3), .collection-stat-print .distribution td:nth-child(3),
  .collection-stat-print .distribution th:nth-child(4), .collection-stat-print .distribution td:nth-child(4) { width: 14%; text-align: center; }
  .collection-stat-print .empty { text-align: center; color: #666; }
  .collection-stat-print .signatures { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; margin-top: 28px; font-size: 13px; }
  .collection-stat-print .signatures span { min-height: 34px; border-bottom: 1px solid #222; }
`;
