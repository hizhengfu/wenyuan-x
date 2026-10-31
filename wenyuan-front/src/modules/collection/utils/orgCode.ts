export interface OrgCodeChange {
  id: string;
  oldCode: string;
  newCode: string;
  reason: string;
  correction: boolean;
  operatorId: string;
  operatedAt: number;
}
export interface OrgCodeContext {
  orgId: string;
  currentCode: string;
  used: boolean;
  canEdit: boolean;
  stateHash: string;
  history: OrgCodeChange[];
}
export function readOrgCodeContext(response: unknown, orgId: string): OrgCodeContext {
  const value = (response as { data?: OrgCodeContext })?.data;
  const text = (s: unknown) => typeof s === 'string' && !!s.trim();
  if (!value || value.orgId !== orgId || !text(value.currentCode) || !text(value.stateHash)
    || typeof value.used !== 'boolean' || typeof value.canEdit !== 'boolean' || !Array.isArray(value.history)
    || value.history.some(row => !row || ![row.id, row.oldCode, row.newCode, row.reason, row.operatorId].every(text)
      || typeof row.correction !== 'boolean' || !Number.isFinite(row.operatedAt))) {
    throw new Error('收藏单位代码状态不完整，请重新加载核对');
  }
  return value;
}
