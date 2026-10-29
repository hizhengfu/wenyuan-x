import { cloneDeep } from 'lodash-es';
import type { SetAmendment, SetRelationSnapshot } from '../api/change';

const object = (v: unknown): v is Record<string, any> => !!v && typeof v === 'object' && !Array.isArray(v);
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim();
/** Jackson omits empty arrays; absent arrays in a present amendment mean an empty relation set. */
export function readSetAmendment(value: unknown, parentId?: string, orgId?: string): SetAmendment | undefined {
  if (value == null) return undefined;
  if (!object(value)) throw new Error('套件订正资料格式不完整，请重新读取');
  const before = value.before === undefined ? [] : value.before;
  const after = value.after === undefined ? [] : value.after;
  const identities = value.identities === undefined ? [] : value.identities;
  if (![before, after, identities].every(Array.isArray)) throw new Error('套件订正清单不完整，请重新读取');
  for (const row of [...before, ...after]) {
    if (
      !object(row) ||
      typeof row.partNo !== 'string' ||
      typeof row.partName !== 'string' ||
      !['MAIN', 'ATTACH', 'PART'].includes(row.relation) ||
      !Number.isInteger(row.quantity) ||
      typeof row.unit !== 'string' ||
      !Number.isInteger(row.sort) ||
      (row.id != null && !text(row.id)) ||
      (row.childCollectionId != null && !text(row.childCollectionId))
    )
      throw new Error('套件关系内容不完整，请核对原申请');
  }
  if (
    before.some((r: any) => !text(r.id)) ||
    new Set(before.map((r: any) => r.id)).size !== before.length ||
    after.some((r: any) => r.id && !before.some((b: any) => b.id === r.id)) ||
    new Set(after.filter((r: any) => r.id).map((r: any) => r.id)).size !== after.filter((r: any) => r.id).length
  )
    throw new Error('套件原关系标识不一致，请核对原申请');
  const expectedOrg = orgId || identities.find((i: any) => object(i) && i.collectionId === parentId)?.orgId;
  if (
    identities.some(
      (i: any) =>
        !object(i) ||
        !text(i.collectionId) ||
        !text(i.orgId) ||
        !text(i.totalRegNo) ||
        !text(i.name) ||
        (expectedOrg && i.orgId !== expectedOrg),
    ) ||
    new Set(identities.map((i: any) => i.collectionId)).size !== identities.length ||
    (parentId && !identities.some((i: any) => i.collectionId === parentId)) ||
    [...before, ...after].some(
      (r: any) => r.childCollectionId && !identities.some((i: any) => i.collectionId === r.childCollectionId),
    )
  )
    throw new Error('套件关联藏品身份不完整或馆别不一致，请重新核对');
  return cloneDeep({ before, after, identities });
}

function canonical(rows: SetRelationSnapshot[]) {
  return rows
    .map((r) => [
      r.id || null,
      r.childCollectionId || null,
      r.partNo.trim(),
      r.partName.trim(),
      r.relation,
      r.quantity,
      r.unit.trim(),
      r.sort,
    ])
    .map((r) => JSON.stringify(r))
    .sort()
    .join('\n');
}
export function hasSetAmendmentChanges(value?: SetAmendment): boolean {
  return !!value && canonical(value.before) !== canonical(value.after);
}
export function setAmendmentError(value: SetAmendment | undefined, parentId?: string): string | undefined {
  if (!value) return;
  const numbers = new Set<string>(),
    children = new Set<string>();
  for (const row of value.after) {
    if (
      !row.partNo.trim() ||
      row.partNo.trim().length > 32 ||
      !row.partName.trim() ||
      row.partName.trim().length > 200 ||
      !Number.isInteger(row.quantity) ||
      row.quantity <= 0 ||
      !row.unit.trim() ||
      row.unit.trim().length > 16 ||
      !Number.isInteger(row.sort) ||
      row.relation === 'MAIN'
    )
      return '请完整填写分件号、名称、附件或分件关系、正整数数量和计量单位';
    if (numbers.has(row.partNo.trim())) return '同一套件的分件号不能重复';
    numbers.add(row.partNo.trim());
    if (row.childCollectionId) {
      if (row.childCollectionId === parentId || children.has(row.childCollectionId))
        return '独立分件不能重复或指向主件自身';
      children.add(row.childCollectionId);
    }
  }
}
