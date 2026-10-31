import { isRegistered } from './registrationStatus';
import type { CollectionVo } from '../typings/collection';

export const registrationOptions = [
  { label: '正式馆藏', value: 'REGISTERED' },
  { label: '待登记藏品', value: 'PENDING' },
  { label: '登记待核实', value: 'UNVERIFIED' },
  { label: '已退出馆藏', value: 'DEREGISTERED' },
  { label: '全部登记范围', value: 'ALL' },
];
export const sortOptions = [
  { label: '相关度', value: 'RELEVANCE' },
  { label: '登记号升序', value: 'REG_NO_ASC' },
  { label: '登记号降序', value: 'REG_NO_DESC' },
  { label: '名称升序', value: 'NAME_ASC' },
  { label: '登记时间最近', value: 'REGISTERED_DESC' },
  { label: '取得日期最近', value: 'ACQUIRE_DESC' },
];
export const criteriaKeys = [
  'keyword',
  'orgId',
  'registrationScope',
  'nature',
  'lifeStatus',
  'categoryId',
  'totalRegNo',
  'numberMatch',
  'classNo',
  'era',
  'texture',
  'level',
  'source',
  'warehouseId',
  'catalogStatus',
  'completeness',
  'hasImage',
  'hasAttachment',
  'acquireDateStart',
  'acquireDateEnd',
  'registeredDateStart',
  'registeredDateEnd',
  'searchSort',
] as const;
export type SearchCriteria = Record<string, string | number | boolean | null>;
export function readSearchCriteria(query: Record<string, unknown>): SearchCriteria {
  const result: SearchCriteria = { registrationScope: 'REGISTERED', searchSort: 'RELEVANCE', numberMatch: 'CONTAINS' };
  for (const key of criteriaKeys) {
    const raw = Array.isArray(query[key]) ? query[key][0] : query[key];
    if (raw === undefined || raw === null || raw === '') continue;
    if (key === 'hasImage' || key === 'hasAttachment') {
      if (raw === 'true' || raw === true) result[key] = true;
      if (raw === 'false' || raw === false) result[key] = false;
    } else if (key.endsWith('DateStart') || key.endsWith('DateEnd')) {
      if (Number.isFinite(Number(raw))) result[key] = Number(raw);
    } else result[key] = String(raw).trim();
  }
  if (!registrationOptions.some((o) => o.value === result.registrationScope)) result.registrationScope = 'REGISTERED';
  if (!sortOptions.some((o) => o.value === result.searchSort)) result.searchSort = 'RELEVANCE';
  if (result.numberMatch !== 'EXACT') result.numberMatch = 'CONTAINS';
  return result;
}
export function registrationLabel(row: CollectionVo) {
  if (row.lifeStatus === 'DEREGISTERED') return '已退出馆藏';
  if (isRegistered(row)) return row.historicalConfirmedAt != null ? '已登账·历史迁入' : '正式馆藏';
  return row.lifeStatus === 'PENDING' && row.catalogStatus !== 'APPROVED' ? '待登记' : '登记待核实';
}
export function collectionDossierTarget(row: CollectionVo, searchReturn?: string) {
  return {
    path: '/collection/ledger',
    query: {
      collectionId: String(row.id),
      keyword: row.totalRegNo || '',
      ...(row.orgId ? { orgId: row.orgId } : {}),
      searchOrigin: 'collection-search',
      ...(searchReturn ? { searchReturn } : {}),
    },
  };
}
/** 只返回藏品检索页；旧链接沿用原有的检索内容和馆范围。 */
export function collectionSearchReturnTarget(query: Record<string, unknown>): string {
  const firstText = (value: unknown) => {
    const first = Array.isArray(value) ? value[0] : value;
    return typeof first === 'string' ? first.trim() : '';
  };
  const saved = firstText(query.searchReturn);
  if (saved === '/collection/search' || saved.startsWith('/collection/search?')) return saved;
  const params = new URLSearchParams();
  for (const key of ['keyword', 'orgId']) {
    const value = firstText(query[key]);
    if (value) params.set(key, value);
  }
  return `/collection/search${params.size ? `?${params}` : ''}`;
}
export function validateSelectedRows(ids: readonly string[], rows: CollectionVo[], orgId: unknown) {
  const map = new Map(rows.map((row) => [String(row.id), row]));
  if (ids.some((id) => !map.has(id) || (orgId && map.get(id)?.orgId !== orgId)))
    throw new Error('部分已选藏品已不可读取或收藏单位发生变化，请重新选择');
  return ids.map((id) => map.get(id)!);
}
export function csvCell(value: unknown) {
  let text = String(value ?? '');
  if (/^[=+\-@\t\r]/.test(text)) text = "'" + text;
  return '"' + text.replaceAll('"', '""') + '"';
}
