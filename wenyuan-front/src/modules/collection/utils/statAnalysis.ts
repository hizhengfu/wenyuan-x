import { isRegistered } from './registrationStatus';
import type { CollectionOrgOverviewNode, CollectionOrgOverviewStats, CollectionVo } from '../typings/collection';
import type { CollectionStatSummary } from './statReport';
import type { CollectionSurveyExportVo } from './surveyExport';
import { formatLocalDateValue } from '@/utils/datetime';

export type StatScopeMode = 'all' | 'direct' | 'aggregate';

export interface StatScopeSelection {
  mode: StatScopeMode;
  orgId?: string | null;
}

export interface OrgStatRow {
  id: string;
  name: string;
  code?: string | null;
  depth: number;
  childCount: number;
  directTotal: number;
  aggregateTotal: number;
  inStoreCount: number;
  flowCount: number;
  imageRate: number;
  attachmentRate: number;
}

export interface CollectionDataQualityItem {
  key: 'basicArchive' | 'image' | 'attachment' | 'warehouse' | 'catalogApproved';
  label: string;
  count: number;
  total: number;
  rate: number;
  value: string;
  note: string;
}

export function isFormalCollection(row: CollectionVo) {
  return (
    isRegistered(row) &&
    row.lifeStatus !== 'DEREGISTERED'
  );
}
export function registrationExclusions(rows: readonly CollectionVo[]) {
  return rows
    .filter((row) => !isFormalCollection(row))
    .map((row) => ({
      collectionId: row.id,
      totalRegNo: row.totalRegNo,
      name: row.name,
      reason:
        row.lifeStatus === 'DEREGISTERED'
          ? '已退出，原总账保留'
          : row.lifeStatus === 'PENDING' && row.catalogStatus !== 'APPROVED'
            ? '尚未正式登记'
            : '历史正式登记依据待核实',
    }));
}
type CollectionWithOrgName = CollectionVo & { orgName?: string | null };

function cleanText(value: unknown, fallback: string) {
  const text = String(value ?? '').trim();
  return text || fallback;
}

function hasText(value: unknown) {
  return String(value ?? '').trim() !== '';
}

function percent(value: number, total: number) {
  if (!total) return 0;
  return Math.round((value / total) * 100);
}

function groupByCount<T>(items: readonly T[], resolve: (item: T) => string) {
  return items.reduce<Record<string, number>>((acc, item) => {
    const key = resolve(item);
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});
}

function parseDate(value: unknown) {
  if (value === undefined || value === null || value === '') return null;
  const date = typeof value === 'number' ? new Date(value) : new Date(String(value));
  return Number.isNaN(date.getTime()) ? null : date;
}

function hasImage(row: CollectionVo) {
  return Boolean(String(row.coverImage ?? '').trim()) || Number(row.mediaCount ?? 0) > 0;
}

function hasAttachment(row: CollectionVo) {
  return Number(row.attachmentCount ?? 0) > 0;
}

function hasWarehouse(row: CollectionVo) {
  return hasText(row.warehouseId) || hasText(row.warehouseName);
}

function hasBasicArchive(row: CollectionVo) {
  return (
    hasText(row.name) &&
    (hasText(row.categoryId) || hasText(row.categoryName)) &&
    (row.nature !== 'RELIC' || hasText(row.level)) &&
    Number(row.quantity ?? 0) > 0 &&
    hasText(row.unit) &&
    hasText(row.source) &&
    parseDate(row.acquireDate) !== null
  );
}

function isCatalogApproved(row: CollectionVo) {
  return String(row.catalogStatus ?? '').toUpperCase() === 'APPROVED';
}

function qualityItem(
  key: CollectionDataQualityItem['key'],
  label: string,
  count: number,
  total: number,
  noteSuffix: string,
): CollectionDataQualityItem {
  const rate = percent(count, total);
  return {
    key,
    label,
    count,
    total,
    rate,
    value: `${rate}%`,
    note: `${count}/${total} 条${noteSuffix}`,
  };
}

export function buildCollectionDataQualityItems(records: readonly CollectionVo[]): CollectionDataQualityItem[] {
  const rows = records.filter(isFormalCollection);
  const total = rows.length;
  return [
    qualityItem('basicArchive', '基础档案完整率', rows.filter(hasBasicArchive).length, total, '基础字段完整'),
    qualityItem('image', '图像采集率', rows.filter(hasImage).length, total, '有图像'),
    qualityItem('attachment', '资料附件率', rows.filter(hasAttachment).length, total, '有资料附件'),
    qualityItem('warehouse', '库位完整率', rows.filter(hasWarehouse).length, total, '已关联库位'),
    qualityItem('catalogApproved', '编目完成率', rows.filter(isCatalogApproved).length, total, '编目已通过'),
  ];
}

export function buildCollectionStatSummary(
  records: readonly CollectionVo[],
  today: Date = new Date(),
  orgNames: Record<string, string> = {},
): CollectionStatSummary {
  const rows = records.filter(isFormalCollection);
  const imageCount = rows.filter(hasImage).length;
  const attachmentCount = rows.filter(hasAttachment).length;
  return {
    total: rows.length,
    pendingRegistrationTotal: records.filter(
      (r) => !isRegistered(r) && r.lifeStatus === 'PENDING' && r.catalogStatus !== 'APPROVED',
    ).length,
    unverifiedRegistrationTotal: records.filter(
      (r) =>
        !isRegistered(r) &&
        r.lifeStatus !== 'DEREGISTERED' &&
        (r.lifeStatus !== 'PENDING' || r.catalogStatus === 'APPROVED'),
    ).length,
    retiredTotal: records.filter((r) => r.lifeStatus === 'DEREGISTERED').length,
    byLevel: groupByCount(
      rows.filter((row) => row.nature === 'RELIC'),
      (row) => cleanText(row.level, '未定级'),
    ),
    byStatus: groupByCount(rows, (row) => cleanText(row.lifeStatus, '未设置')),
    byCatalogStatus: groupByCount(rows, (row) => cleanText(row.catalogStatus, '未设置')),
    byCategory: groupByCount(rows, (row) => cleanText(row.categoryName || row.categoryId, '未分类')),
    byEra: groupByCount(rows, (row) => cleanText(row.era, '未标注')),
    bySource: groupByCount(rows, (row) => cleanText(row.source, '未设置')),
    byTexture: groupByCount(rows, (row) => cleanText(row.texture, '未设置')),
    byCompleteness: groupByCount(rows, (row) => cleanText(row.completeness, '未设置')),
    byPreserveState: groupByCount(rows, (row) => cleanText(row.preserveState, '未设置')),
    byWarehouse: groupByCount(rows, (row) => cleanText(row.warehouseName || row.warehouseId, '未登记库位')),
    byOrg: groupByCount(rows as readonly CollectionWithOrgName[], (row) =>
      cleanText(row.orgName || (row.orgId ? orgNames[row.orgId] : '') || row.orgId, '未设置'),
    ),
    imageCount,
    attachmentCount,
    imageRate: percent(imageCount, rows.length),
    attachmentRate: percent(attachmentCount, rows.length),
    registeredThisYear: rows.filter((row) => {
      const date = parseDate(row.registeredAt);
      return date?.getFullYear() === today.getFullYear();
    }).length,
    registeredThisMonth: rows.filter((row) => {
      const date = parseDate(row.registeredAt);
      return date?.getFullYear() === today.getFullYear() && date.getMonth() === today.getMonth();
    }).length,
  };
}

export function flattenOrgNodes(
  nodes: readonly CollectionOrgOverviewNode[],
  depth = 0,
): (CollectionOrgOverviewNode & { depth: number })[] {
  return nodes.flatMap((node) => [{ ...node, depth }, ...flattenOrgNodes(node.children ?? [], depth + 1)]);
}

export function findOrgNode(
  nodes: readonly CollectionOrgOverviewNode[],
  orgId?: string | null,
): CollectionOrgOverviewNode | null {
  if (!orgId) return null;
  for (const node of nodes) {
    if (node.id === orgId) return node;
    const child = findOrgNode(node.children ?? [], orgId);
    if (child) return child;
  }
  return null;
}

export function collectOrgIds(node: CollectionOrgOverviewNode | null): string[] {
  if (!node) return [];
  return [node.id, ...(node.children ?? []).flatMap((child) => collectOrgIds(child))];
}

export function filterCollectionsByOrgScope(
  rows: readonly CollectionVo[],
  orgNodes: readonly CollectionOrgOverviewNode[],
  selection: StatScopeSelection,
) {
  if (selection.mode === 'all' || !selection.orgId) return [...rows];
  if (selection.mode === 'direct') return rows.filter((row) => row.orgId === selection.orgId);
  const orgIds = new Set(collectOrgIds(findOrgNode(orgNodes, selection.orgId)));
  if (!orgIds.size) orgIds.add(selection.orgId);
  return rows.filter((row) => row.orgId && orgIds.has(row.orgId));
}

export function statScopeLabel(node: Pick<CollectionOrgOverviewNode, 'name'> | null, mode: StatScopeMode) {
  if (mode === 'all' || !node) return '全馆汇总';
  return mode === 'direct' ? `${node.name}直属` : `${node.name}含分馆`;
}

function orgRate(stats: CollectionOrgOverviewStats, key: 'imageCount' | 'attachmentCount') {
  return percent(stats[key] ?? 0, stats.total ?? 0);
}

function flowCount(stats: CollectionOrgOverviewStats) {
  const status = stats.byStatus ?? {};
  return (status.PRE_OUT ?? 0) + (status.IN_USE ?? 0) + (status.RESTORING ?? 0) + (status.ON_LOAN ?? 0);
}

export function buildOrgStatRows(nodes: readonly CollectionOrgOverviewNode[]): OrgStatRow[] {
  return flattenOrgNodes(nodes).map((node) => ({
    id: node.id,
    name: node.name,
    code: node.code,
    depth: node.depth,
    childCount: node.children?.length ?? 0,
    directTotal: node.direct.total,
    aggregateTotal: node.aggregate.total,
    inStoreCount: node.aggregate.byStatus?.IN_STORE ?? 0,
    flowCount: flowCount(node.aggregate),
    imageRate: orgRate(node.aggregate, 'imageCount'),
    attachmentRate: orgRate(node.aggregate, 'attachmentCount'),
  }));
}

export function buildOrgNameMap(nodes: readonly CollectionOrgOverviewNode[]) {
  return Object.fromEntries(flattenOrgNodes(nodes).map((node) => [node.id, node.name]));
}

function dimensionText(value: CollectionVo['dimensions']) {
  if (!value) return '';
  if (typeof value === 'string') return value;
  return Object.entries(value)
    .filter(([, item]) => item !== undefined && item !== null && String(item).trim() !== '')
    .map(([key, item]) => `${key}:${item}`)
    .join('；');
}

export function toSurveyExportRows(
  rows: readonly CollectionVo[],
  orgNames: Record<string, string> = {},
): CollectionSurveyExportVo[] {
  return rows.filter(isFormalCollection).map((row) => ({
    totalRegNo: row.totalRegNo,
    classNo: row.classNo,
    name: row.name,
    era: row.era,
    categoryName: row.categoryName || row.categoryId,
    texture: row.texture,
    dimensions: dimensionText(row.dimensions),
    weight: row.weight,
    completeness: row.completeness,
    completeStatus: row.completeStatus,
    source: row.source,
    acquireDate: row.acquireDate == null ? undefined : formatLocalDateValue(row.acquireDate),
    level: row.level,
    sourceDesc: row.sourceDesc,
    orgName: row.orgId ? orgNames[row.orgId] || row.orgId : undefined,
    image: hasImage(row) ? '有' : '无',
  }));
}
