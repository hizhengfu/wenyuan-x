import request from '@/utils/request';
import type { CollectionCatalogContentVo } from '../typings/collection';
export interface ChangeCorrectionOrigin {
  previousChangeId: string;
  previousInstanceId: string;
  previousStatus: string;
  submittedArchiveId: string;
  submittedDigest: string;
  reason: string;
  evidenceFileIds: string;
  recordedBy: string;
  recordedAt: number | string;
}
export interface SetRelationSnapshot {
  id?: string | null;
  childCollectionId?: string | null;
  partNo: string;
  partName: string;
  relation: 'MAIN' | 'ATTACH' | 'PART';
  quantity: number;
  unit: string;
  sort: number;
}
export interface SetIdentitySnapshot {
  collectionId: string;
  orgId: string;
  totalRegNo: string;
  name: string;
  registeredAt?: number | string | null;
}
export interface SetAmendment {
  before: SetRelationSnapshot[];
  after: SetRelationSnapshot[];
  identities: SetIdentitySnapshot[];
}
export interface CollectionChangeVo {
  setAmendment?: SetAmendment;
  correction?: ChangeCorrectionOrigin;
  id?: string;
  orgId?: string;
  collectionId?: string;
  totalRegNo?: string;
  collectionName?: string;
  collectionEvidenceIssue?: string;
  reason?: string;
  before?: CollectionCatalogContentVo;
  content?: CollectionCatalogContentVo;
  keeperUserId?: string;
  leaderUserId?: string;
  evidenceFileIds?: string;
  signedChangeFileIds?: string;
  paperLedgerRef?: string;
  paperLedgerBookId?: string;
  paperLedgerPageNo?: number;
  paperUpdatedAt?: number;
  appliedAt?: number;
  appliedBy?: string;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: number;
}
const BASE = '/collection/change';
export const changeApi = {
  page: (params: any) => request({ url: `${BASE}/page`, method: 'get', params }),
  corrections: (id: string) => request({ url: `${BASE}/${id}/corrections`, method: 'get' }),
  createCorrection: (
    id: string,
    data: { requestKey: string; expectedInstanceId: string; reason: string; evidenceFileIds: string },
  ) => request({ url: `${BASE}/${id}/corrections`, method: 'post', data }),
  archive: (id: string, signal?: AbortSignal) => request({ url: `${BASE}/${id}/archive`, method: 'get', signal }),
  get: (id: string) => request({ url: `${BASE}/${id}`, method: 'get' }),
  proposal: (id: string, includeSetRelations = false) =>
    request({
      url: `${BASE}/proposal/${id}`,
      method: 'get',
      ...(includeSetRelations ? { params: { includeSetRelations: true } } : {}),
    }),
  save: (data: CollectionChangeVo) => request({ url: `${BASE}/save`, method: 'post', data }),
  submit: (id: string) => request({ url: `${BASE}/${id}/submit`, method: 'post' }),
  cancel: (id: string) => request({ url: `${BASE}/${id}/cancel`, method: 'post' }),
  flow: (id: string) => request({ url: `${BASE}/${id}/flow`, method: 'get' }),
  execute: (id: string, data: Pick<CollectionChangeVo, 'signedChangeFileIds' | 'paperLedgerBookId' | 'paperLedgerPageNo' | 'paperUpdatedAt'>) =>
    request({ url: `${BASE}/${id}/execute`, method: 'post', data }),
};
