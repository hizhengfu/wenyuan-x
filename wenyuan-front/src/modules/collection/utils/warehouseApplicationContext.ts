import { cloneDeep } from 'lodash-es';
export type WarehouseApplicationContext = {
  application?: Record<string, any> | null;
  orgId: string;
  orgName: string;
  actorId: string;
  actorName: string;
  revision: string;
  canSave: boolean;
  canSubmit: boolean;
  canCancel: boolean;
  canVoid: boolean;
};
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim();
export function warehouseApplicationResponse(response: any, id?: string): WarehouseApplicationContext {
  const d = response?.data,
    a = d?.application;
  if (
    (response?.status !== undefined && response.status !== 200) ||
    !d ||
    !text(d.orgId) ||
    !text(d.orgName) ||
    !text(d.actorId) ||
    !text(d.actorName) ||
    !text(d.revision) ||
    !/^[0-9a-f]{64}$/.test(d.revision) ||
    ['canSave', 'canSubmit', 'canCancel', 'canVoid'].some((k) => typeof d[k] !== 'boolean') ||
    (id ? a?.id !== id || a.orgId !== d.orgId : a != null)
  )
    throw Error('申请响应缺少一致的原馆、账号或版本，请重新读取');
  if (a) {
    if (
      typeof a.warehouseIds !== 'string' ||
      typeof a.entrantIds !== 'string' ||
      (a.archives != null && !Array.isArray(a.archives)) ||
      (a.evidenceErrors != null && (!Array.isArray(a.evidenceErrors) || a.evidenceErrors.some((issue: unknown) => !text(issue)))) ||
      ['plannedStart', 'plannedEnd', 'actualEntryAt', 'actualExitAt', 'approvedAt'].some(
        (k) => a[k] != null && (typeof a[k] !== 'number' || !Number.isSafeInteger(a[k])),
      )
    )
      throw Error('申请资料或时间格式不完整，请重新核对');
  }
  const draft = !a || (a.flowStatus === 'DRAFT' && !a.flowInstanceId && a.actualEntryAt == null);
  if (
    (d.canSave && !draft) ||
    (d.canSubmit && (!a || !draft)) ||
    (d.canCancel && (!a || a.flowStatus !== 'AUDIT' || a.actualEntryAt != null)) ||
    (d.canVoid && (!a || ['PASS', 'VOID'].includes(a.flowStatus) || a.actualEntryAt != null))
  )
    throw Error('申请可办理状态与原记录不一致');
  return cloneDeep(d);
}
const csv = (value: any) =>
  String(value ?? '')
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean)
    .sort()
    .join(',');
const items = (value: any) =>
  Array.isArray(value) && value.every((x) => typeof x === 'string')
    ? [...new Set(value)].sort()
    : value == null
      ? []
      : undefined;
export function sameWarehouseProposal(actual: any, expected: Record<string, any>): boolean {
  if (
    !actual ||
    actual.purpose !== String(expected.purpose ?? '').trim() ||
    actual.plannedStart !== expected.plannedStart ||
    actual.plannedEnd !== expected.plannedEnd ||
    ['warehouseIds', 'entrantIds', 'attachmentIds'].some((k) => csv(actual[k]) !== csv(expected[k]))
  )
    return false;
  const a = actual.dossier,
    b = expected.dossier;
  if (
    !a ||
    !b ||
    ['kind', 'keeperUserId', 'leaderUserId', 'identityFileIds', 'shortageReason', 'shortageFileIds'].some(
      (k) => (a[k] ?? '') !== (b[k] ?? ''),
    )
  )
    return false;
  return ['escortUserIds', 'externalNames'].every(
    (k) =>
      items(a[k]) !== undefined &&
      items(b[k]) !== undefined &&
      JSON.stringify(items(a[k])) === JSON.stringify(items(b[k])),
  );
}
