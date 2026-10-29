import type { CollectionImportConfirmationVo, CollectionImportValidateResultVo, CollectionWordImportPreviewVo } from '../typings/collection';

const object = (v: unknown): v is Record<string, any> => !!v && typeof v === 'object' && !Array.isArray(v);
const text = (v: unknown): v is string => typeof v === 'string' && v.trim().length > 0;
const count = (v: unknown): v is number => Number.isSafeInteger(v) && Number(v) >= 0;
const ids = (v: unknown): v is string[] => Array.isArray(v) && v.every(text) && new Set(v).size === v.length;
function body(response: unknown) {
  if (!object(response) || !object(response.data) || typeof response.data.valid !== 'boolean') throw new Error('导入响应不完整，请重新核对');
  return response.data;
}
function errors(data: Record<string, any>) {
  if (!Array.isArray(data.errors) || data.errors.some((v: any) => !object(v) || !count(v.rowNo) || !text(v.field) || !text(v.message))) throw new Error('导入错误信息不完整，请重新核对');
  if (!data.valid && !data.errors.length) return [{ rowNo: 0, field: 'server', message: '服务端未通过本次导入校验，请重新核对' }];
  if (data.valid && data.errors.length) throw new Error('导入响应状态不一致，请重新核对');
  return data.errors;
}
export function importConfirmation(value: unknown): CollectionImportConfirmationVo {
  if (!object(value) || !text(value.orgId) || !text(value.orgName) || typeof value.validationHash !== 'string' || !/^[0-9a-f]{64}$/.test(value.validationHash)) throw new Error('缺少本次导入的收藏单位或核对依据，请重新预校验');
  return { orgId: value.orgId, orgName: value.orgName, validationHash: value.validationHash };
}
export function ledgerValidation(response: unknown, rowCount: number): CollectionImportValidateResultVo {
  const data = body(response);
  const result: Record<string, any> = { ...data, errors: errors(data) };
  if (data.valid) {
    if (!count(data.createCount) || !count(data.updateCount) || data.createCount + data.updateCount !== rowCount || !Array.isArray(data.items) || data.items.length !== rowCount) throw new Error('导入匹配数量不完整，请重新预校验');
    result.confirmation = importConfirmation(data.confirmation);
  }
  return result as CollectionImportValidateResultVo;
}
export function ledgerCommit(response: unknown, expected: CollectionImportValidateResultVo) {
  const data = body(response);
  const result: Record<string, any> = { ...data, errors: errors(data) };
  if (data.valid) {
    if (!ids(data.createdIds) || !ids(data.updatedIds) || !ids(data.unchangedIds) || data.createdIds.length !== expected.createCount || data.updatedIds.length + data.unchangedIds.length !== expected.updateCount || new Set([...data.createdIds, ...data.updatedIds, ...data.unchangedIds]).size !== data.createdIds.length + data.updatedIds.length + data.unchangedIds.length) throw new Error('导入结果与本次确认不一致，请核对处理结果后再操作');
  }
  return result as { valid: boolean; errors: CollectionImportValidateResultVo['errors']; createdIds: string[]; updatedIds: string[]; unchangedIds: string[] };
}
export function wordValidation(response: unknown, type: string, collectionId?: string): CollectionWordImportPreviewVo {
  const data = body(response);
  const result: Record<string, any> = { ...data, errors: errors(data) };
  if (data.valid) {
    if (data.documentType !== type || !text(data.sourceHash) || !text(data.fingerprint) || !Array.isArray(data.events) || !Array.isArray(data.movements) || !Array.isArray(data.warnings)) throw new Error('Word 预校验资料不完整，请重新核对');
    if (type === 'REGISTRATION' && !object(data.registration)) throw new Error('登记表内容缺失，请重新核对');
    if (type === 'DYNAMIC_TRACKING' && (!text(data.collectionId) || (collectionId && data.collectionId !== collectionId))) throw new Error('动态记录对应藏品已变化，请重新核对');
    result.confirmation = importConfirmation(data.confirmation);
  }
  return result as CollectionWordImportPreviewVo;
}
export function wordCommit(response: unknown, expected: CollectionWordImportPreviewVo) {
  const data = body(response);
  const result: Record<string, any> = { ...data, errors: errors(data) };
  if (data.valid) {
    if (data.documentType !== expected.documentType || !text(data.collectionId) || !text(data.importId) || !count(data.eventCount) || !count(data.movementCount) || data.eventCount !== expected.events.length || data.movementCount !== expected.movements.length || typeof data.createdCollection !== 'boolean' || typeof data.updatedCollection !== 'boolean' || (data.createdCollection && data.updatedCollection)) throw new Error('Word 导入结果与本次确认不一致，请核对处理结果后再操作');
    if (expected.documentType === 'DYNAMIC_TRACKING' && (data.collectionId !== expected.collectionId || data.createdCollection || data.updatedCollection)) throw new Error('动态记录导入结果不一致，请核对处理结果');
  }
  return result as Record<string, any>;
}
