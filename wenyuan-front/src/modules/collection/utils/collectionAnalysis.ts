import type { CollectionVo } from '../typings/collection';
import { isFormalCollection } from './statAnalysis';

export type AnalysisCollection = CollectionVo & { orgName?: string; onExhibition?: boolean };
export type AnalysisDimension =
  | 'categoryId'
  | 'level'
  | 'era'
  | 'texture'
  | 'source'
  | 'lifeStatus'
  | 'warehouseId'
  | 'nature'
  | 'orgId';
export type AnalysisFilters = { keyword: string; useState: string | null } & Record<AnalysisDimension, string[]>;
export const analysisDimensions: { key: AnalysisDimension; label: string; dict?: string }[] = [
  { key: 'categoryId', label: '藏品类别' },
  { key: 'level', label: '文物级别', dict: 'col_relic_level' },
  { key: 'nature', label: '藏品性质' },
  { key: 'era', label: '年代' },
  { key: 'texture', label: '质地', dict: 'col_texture' },
  { key: 'source', label: '来源', dict: 'col_source' },
  { key: 'lifeStatus', label: '状态', dict: 'col_life_status' },
  { key: 'warehouseId', label: '保管位置' },
  { key: 'orgId', label: '馆别' },
];
export function emptyAnalysisFilters(): AnalysisFilters {
  return {
    keyword: '',
    useState: null,
    categoryId: [],
    level: [],
    nature: [],
    era: [],
    texture: [],
    source: [],
    lifeStatus: [],
    warehouseId: [],
    orgId: [],
  };
}
export function analysisValue(row: AnalysisCollection, key: AnalysisDimension): string {
  if (key === 'level' && row.nature !== 'RELIC') return '';
  return String(row[key] ?? '').trim();
}
export function analysisLabel(
  row: AnalysisCollection,
  key: AnalysisDimension,
  dict: (type: string | undefined, value: string) => string,
): string {
  const value = analysisValue(row, key);
  if (key === 'level' && row.nature !== 'RELIC') return '不适用文物级别';
  if (!value) return '未标注';
  if (key === 'categoryId') return row.categoryName || value;
  if (key === 'warehouseId') return row.warehouseName || value;
  if (key === 'orgId') return row.orgName || value;
  if (key === 'nature')
    return (
      ({ RELIC: '文物藏品', OTHER: '其他藏品', SCIENTIFIC_FOSSIL: '科学化石' } as Record<string, string>)[value] ||
      value
    );
  return dict(analysisDimensions.find((d) => d.key === key)?.dict, value);
}
export function filterAnalysisCollections(
  rows: readonly AnalysisCollection[],
  filters: AnalysisFilters,
): AnalysisCollection[] {
  const keyword = filters.keyword.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (!keyword ||
        [row.name, row.totalRegNo, row.auxNo].some((v) =>
          String(v ?? '')
            .toLocaleLowerCase()
            .includes(keyword),
        )) &&
      analysisDimensions.every(({ key }) => !filters[key].length || filters[key].includes(analysisValue(row, key))) &&
      (!filters.useState ||
        (filters.useState === 'EXHIBITION' ? row.onExhibition === true : row.lifeStatus === 'ON_LOAN')),
  );
}
export function collectionQuantities(rows: readonly CollectionVo[]) {
  const byUnit: Record<string, number> = {};
  let unknown = 0;
  for (const row of rows.filter(isFormalCollection)) {
    const unit = row.unit?.trim(),
      quantity = row.quantity;
    if (!unit || !Number.isSafeInteger(quantity) || Number(quantity) <= 0) {
      unknown++;
      continue;
    }
    byUnit[unit] = (byUnit[unit] || 0) + Number(quantity);
  }
  return { byUnit, unknown };
}
export function quantityText(byUnit: Record<string, number>, empty = '0') {
  return (
    Object.entries(byUnit)
      .sort(([a], [b]) => a.localeCompare(b, 'zh-Hans-CN'))
      .map(([unit, value]) => `${value} ${unit}`)
      .join(' / ') || empty
  );
}
export function buildCrossAnalysis(
  rows: readonly AnalysisCollection[],
  first: AnalysisDimension,
  second: AnalysisDimension,
  dict: (type: string | undefined, value: string) => string,
) {
  const groups = new Map<string, { first: string; second: string; rows: AnalysisCollection[] }>();
  const formal = rows.filter(isFormalCollection);
  for (const row of formal) {
    const a = analysisLabel(row, first, dict),
      b = analysisLabel(row, second, dict),
      key = JSON.stringify([
        analysisValue(row, first),
        first === 'level' && row.nature !== 'RELIC' ? 'NOT_APPLICABLE' : '',
        analysisValue(row, second),
        second === 'level' && row.nature !== 'RELIC' ? 'NOT_APPLICABLE' : '',
      ]);
    const group = groups.get(key) || { first: a, second: b, rows: [] };
    group.rows.push(row);
    groups.set(key, group);
  }
  return [...groups.entries()]
    .map(([id, group]) => {
      const q = collectionQuantities(group.rows);
      return {
        id,
        first: group.first,
        second: group.second,
        count: group.rows.length,
        percent: formal.length ? Math.round((group.rows.length * 10000) / formal.length) / 100 : 0,
        quantity: quantityText(q.byUnit, q.unknown ? '待核实' : '0'),
        unknown: q.unknown,
      };
    })
    .sort(
      (a, b) =>
        b.count - a.count ||
        a.first.localeCompare(b.first, 'zh-Hans-CN') ||
        a.second.localeCompare(b.second, 'zh-Hans-CN'),
    );
}
/** 当前仍在册记录的登记月份分布，不推算历史馆藏增减。 */
export function buildRegistrationTrend(rows: readonly CollectionVo[]) {
  const counts = new Map<string, number>();
  for (const row of rows.filter(isFormalCollection)) {
    const date = typeof row.registeredAt === 'number' ? new Date(row.registeredAt) : new Date(String(row.registeredAt));
    if (Number.isNaN(date.getTime())) continue;
    const month = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
    counts.set(month, (counts.get(month) || 0) + 1);
  }
  return [...counts].sort(([a], [b]) => a.localeCompare(b)).map(([month, count]) => ({ month, count }));
}
