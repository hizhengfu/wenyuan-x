import { describe, it, expect } from 'vitest';
import {
  emptyAnalysisFilters,
  filterAnalysisCollections,
  collectionQuantities,
  quantityText,
  buildCrossAnalysis,
  buildRegistrationTrend,
  type AnalysisCollection,
} from '@/modules/collection/utils/collectionAnalysis';
import { buildCollectionStatSummary, filterCollectionsByOrgScope } from '@/modules/collection/utils/statAnalysis';
import { buildStatDistributionRows } from '@/modules/collection/utils/statReport';
const row = (id: string, overrides: Partial<AnalysisCollection> = {}): AnalysisCollection => ({
  id,
  name: id,
  orgId: 'main',
  registeredAt: '2026-01-10T10:00:00',
  lifeStatus: 'IN_STORE',
  nature: 'RELIC',
  categoryId: 'A',
  level: 'FIRST',
  quantity: 1,
  unit: '件',
  ...overrides,
});
const records = [
  row('one'),
  row('two', { categoryId: 'B', lifeStatus: 'IN_USE', onExhibition: true }),
  row('three', { categoryId: 'C', level: 'SECOND' }),
  row('four', { orgId: 'branch', categoryId: 'B', level: 'SECOND', lifeStatus: 'IN_USE' }),
  row('five', { orgId: 'branch', categoryId: 'B', level: 'SECOND', lifeStatus: 'ON_LOAN', unit: '套', quantity: 2 }),
];
describe('组合分析的实际业务口径', () => {
  it('同维度多选为或，跨维度为且，并在馆别范围内继续筛选', () => {
    const filters = emptyAnalysisFilters();
    filters.categoryId = ['A', 'B'];
    filters.level = ['FIRST', 'SECOND'];
    filters.orgId = ['branch'];
    expect(filterAnalysisCollections(records, filters).map((r) => r.id)).toEqual(['four', 'five']);
    filters.useState = 'EXHIBITION';
    expect(filterAnalysisCollections(records, filters)).toEqual([]);
    filters.orgId = [];
    expect(filterAnalysisCollections(records, filters).map((r) => r.id)).toEqual(['two']);
    filters.useState = 'LOAN';
    expect(filterAnalysisCollections(records, filters).map((r) => r.id)).toEqual(['five']);
    const nodes: any = [{ id: 'main', children: [{ id: 'branch', children: [] }] }];
    expect(filterCollectionsByOrgScope(records, nodes, { mode: 'direct', orgId: 'main' }).length).toBe(3);
    expect(filterCollectionsByOrgScope(records, nodes, { mode: 'aggregate', orgId: 'main' }).length).toBe(5);
  });
  it('不把其他提用视为展出，不把待登记与已退出混入实物数量', () => {
    const mixed = [
      ...records,
      row('pending', { registeredAt: undefined, quantity: 50 }),
      row('retired', { lifeStatus: 'DEREGISTERED', quantity: 70 }),
      row('unknown', { unit: undefined }),
    ];
    const quantities = collectionQuantities(mixed);
    expect(quantities).toEqual({ byUnit: { 件: 4, 套: 2 }, unknown: 1 });
    expect(quantityText(quantities.byUnit)).toContain('4 件');
    expect(quantityText(quantities.byUnit)).toContain('2 套');
    const filters = emptyAnalysisFilters();
    filters.useState = 'EXHIBITION';
    expect(filterAnalysisCollections(mixed, filters).map((r) => r.id)).toEqual(['two']);
  });
  it('交叉分析区分未定级文物与不适用级别的其他藏品，图表的分母仅取文物', () => {
    const values = [
      row('relic', { level: undefined }),
      row('other', { nature: 'OTHER', level: undefined }),
      row('first'),
    ];
    const cross = buildCrossAnalysis(values, 'categoryId', 'level', (_, value) => value);
    expect(cross).toHaveLength(3);
    expect(cross.map((r) => r.second)).toContain('不适用文物级别');
    expect(cross.reduce((sum, r) => sum + r.count, 0)).toBe(3);
    const stat = buildCollectionStatSummary(values);
    expect(stat.byLevel).toEqual({ 未定级: 1, FIRST: 1 });
    expect(
      buildStatDistributionRows(stat)
        .filter((r) => r.section.startsWith('文物级别'))
        .map((r) => r.rate),
    ).toEqual([50, 50]);
  });
  it('月份分布描述当前仍在册的登记时间，不推算历史增减', () => {
    expect(
      buildRegistrationTrend([
        row('a'),
        row('b', { registeredAt: '2026-02-10T00:00:00' }),
        row('exit', { registeredAt: '2025-12-01', lifeStatus: 'DEREGISTERED' }),
        row('draft', { registeredAt: undefined }),
      ]),
    ).toEqual([
      { month: '2026-01', count: 1 },
      { month: '2026-02', count: 1 },
    ]);
  });
});
