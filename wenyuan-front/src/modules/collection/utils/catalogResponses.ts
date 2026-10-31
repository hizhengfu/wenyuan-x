import type { CollectionCatalogVo, CollectionCatalogVersionVo } from '../typings/collection';
import { cloneDeep } from 'lodash-es';
const object = (v: unknown): v is Record<string, any> => !!v && typeof v === 'object' && !Array.isArray(v);
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim();
const integer = (v: unknown) => Number.isSafeInteger(v) && Number(v) >= 0;
const states = ['DRAFT', 'SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED'];
export function readCatalog(response: unknown, id: string): CollectionCatalogVo {
  const row = object(response) ? response.data : null;
  if (!object(row) || row.collectionId !== id || !text(row.id) || !text(row.orgId) || !text(row.orgName) ||
      !object(row.content) || !states.includes(row.status) || !integer(row.version) ||
      !object(row.completeness) || typeof row.completeness.complete !== 'boolean' || !Array.isArray(row.completeness.missingFields) ||
      typeof row.stateHash !== 'string' || !/^[0-9a-f]{64}$/.test(row.stateHash) ||
      typeof row.canEdit !== 'boolean' || typeof row.canReview !== 'boolean') throw new Error('编目资料、当前资格或核对依据不完整，请重新读取');
  return cloneDeep(row) as CollectionCatalogVo;
}
export function catalogResult(response: unknown, expected: CollectionCatalogVo, status: string, nextVersion: number) {
  const row = readCatalog(response, expected.collectionId!);
  if (row.id !== expected.id || row.orgId !== expected.orgId || row.status !== status || row.version !== nextVersion)
    throw new Error('编目处理结果与本次确认不一致，请核对实际结果后再办理');
  return row;
}
export function readCatalogVersions(response: unknown, id: string): CollectionCatalogVersionVo[] {
  const rows = object(response) ? response.data : null;
  if (!Array.isArray(rows) || rows.some(row => !object(row) || !text(row.id) || row.collectionId !== id || !text(row.catalogId) ||
      !integer(row.version) || !states.includes(row.status) || !object(row.content))) throw new Error('编目版本记录不完整，请重新核对');
  return cloneDeep(rows);
}
