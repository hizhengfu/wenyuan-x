import type { CollectionImportConfirmationVo, CollectionImportErrorVo } from '../typings/collection';
const metadata = ['sourceRecordId','originalOrgCode','nature','currentStatus','originalRegisteredAt','originalRegisteredBy','registrationEvidence','registrationEvidenceName','verification','historyVerified'];
export function migrationPayload(rows: Record<string, unknown>[], sourceSystem: string, reason: string, belongsToCurrentMuseum: boolean) {
  return JSON.parse(JSON.stringify({sourceSystem: sourceSystem.trim(), reason: reason.trim(), belongsToCurrentMuseum,
    rows: rows.map(row => ({item: Object.fromEntries(Object.entries(row).filter(([key]) => !metadata.includes(key))),
      ...Object.fromEntries(metadata.filter(key => row[key] !== undefined).map(key => [key,row[key]]))}))}));
}
const count = (n: unknown) => Number.isSafeInteger(n) && Number(n) >= 0;
function receipt(response: any) {
  const data = response?.data;
  if(!data || typeof data.valid !== 'boolean' || !Array.isArray(data.errors) || data.errors.some((e: any) => !e || !count(e.rowNo) || typeof e.field !== 'string' || typeof e.message !== 'string') || (data.valid && data.errors.length)) throw Error('迁移回执不完整，请重新核对');
  return data;
}
export function migrationPreview(response: unknown, expected: number): {valid: boolean; errors: CollectionImportErrorVo[]; registeredCount: number; unverifiedCount: number; createCount: number; confirmation: CollectionImportConfirmationVo} {
  const data = receipt(response);
  if(data.valid && (data.registeredCount !== 0 || !count(data.unverifiedCount) || data.registeredCount + data.unverifiedCount !== expected || data.createCount !== expected || data.updateCount !== 0 || !data.confirmation || typeof data.confirmation.orgId !== 'string' || !data.confirmation.orgId.trim() || typeof data.confirmation.orgName !== 'string' || !data.confirmation.orgName.trim() || !/^[a-f0-9]{64}$/.test(data.confirmation.validationHash))) throw Error('迁移分流数量或目标馆回执不完整');
  return data;
}
export function migrationCommit(response: unknown, expected: number): {valid: boolean; errors: CollectionImportErrorVo[]; createdIds: string[]} {
  const data = receipt(response);
  if(data.valid && (!Array.isArray(data.createdIds) || data.createdIds.length !== expected || data.createdIds.some((id: unknown) => typeof id !== 'string' || !id.trim()) || new Set(data.createdIds).size !== expected || !Array.isArray(data.updatedIds) || data.updatedIds.length || !Array.isArray(data.unchangedIds) || data.unchangedIds.length)) throw Error('迁移结果回执不完整，请核对批次结果');
  return data;
}
