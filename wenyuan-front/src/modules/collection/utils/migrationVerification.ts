import type { CollectionCatalogContentVo } from '../typings/collection';
export const verificationStatuses = { DRAFT: '材料草稿', SUBMITTED: '待独立复核', REJECTED: '已退回', APPROVED: '复核通过', WITHDRAWN: '已撤回' } as const;
export type VerificationStatus = keyof typeof verificationStatuses;
export interface MigrationVerificationContent {
  originalRegisteredAt: number | null;
  originalRegisteredBy: string;
  registrationEvidence: string | null;
  historyVerified: boolean;
  matchesOriginalLedger: boolean;
  reason: string;
  originalDateMissingReason?: string | null;
  originalRegistrarMissingReason?: string | null;
  classificationMissingReason?: string | null;
}
export interface MigrationVerificationRecord {
  id: string; status: VerificationStatus; content: MigrationVerificationContent; collectionSnapshot: CollectionCatalogContentVo;
  submittedBy?: string; submittedAt?: number; reviewedBy?: string; reviewedAt?: number; opinion?: string;
  recordedBy: string; recordedAt: number;
}
export interface MigrationVerification {
  collectionId: string; totalRegNo: string;
  missingFields?: string[];
  collectionSnapshot?: CollectionCatalogContentVo;
  source: { batchId?: string; declaredVerification?: string; originalDateMissingReason?: string; originalRegistrarMissingReason?: string; classificationMissingReason?: string; verification: 'CONFIRMED' | 'UNVERIFIED'; sourceSystem: string; originalRegisteredAt?: string; originalRegisteredBy?: string; registrationEvidence?: string; historyVerified: boolean };
  record?: MigrationVerificationRecord; history: MigrationVerificationRecord[];
  stateHash: string; canEdit: boolean; canReview: boolean; canWithdraw: boolean; blockReason?: string;
}
export function readMigrationVerification(response: any, collectionId: string): MigrationVerification {
  const data = response?.data;
  if (!data || data.collectionId !== collectionId || typeof data.totalRegNo !== 'string' || !/^[a-f0-9]{64}$/.test(data.stateHash || '') ||
      !['CONFIRMED', 'UNVERIFIED'].includes(data.source?.verification) || !Array.isArray(data.history) ||
      ['canEdit', 'canReview', 'canWithdraw'].some(key => typeof data[key] !== 'boolean') ||
      data.history.some((item: any) => !item?.id || !Object.hasOwn(verificationStatuses, item.status) || !item.content || !item.collectionSnapshot || !item.recordedBy || !Number.isFinite(item.recordedAt)) ||
      (data.record?.id || null) !== (data.history[0]?.id || null)) throw new Error('迁移核验回执不完整，请重新加载');
  return data;
}
export function verificationForm(data: MigrationVerification): MigrationVerificationContent {
  const record = data.record?.content;
  const originalDate = data.source.originalRegisteredAt ? new Date(data.source.originalRegisteredAt).getTime() : null;
  return { originalRegisteredAt: record ? record.originalRegisteredAt ?? null : (Number.isFinite(originalDate) ? originalDate : null),
    originalRegisteredBy: record ? record.originalRegisteredBy || '' : data.source.originalRegisteredBy || '',
    registrationEvidence: record ? record.registrationEvidence || null : data.source.registrationEvidence || null,
    historyVerified: record?.historyVerified ?? data.source.historyVerified,
    matchesOriginalLedger: record?.matchesOriginalLedger ?? false, reason: record?.reason || '',
    originalDateMissingReason: record ? record.originalDateMissingReason || '' : data.source.originalDateMissingReason || '',
    originalRegistrarMissingReason: record ? record.originalRegistrarMissingReason || '' : data.source.originalRegistrarMissingReason || '',
    classificationMissingReason: data.missingFields?.includes('分类号') === false ? '' : record ? record.classificationMissingReason || '' : data.source.classificationMissingReason || '' };
}
