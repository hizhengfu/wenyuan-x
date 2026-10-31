/**
 * 藏品注销（退出馆藏）写操作权限码，与后端 DeregistrationController 的
 * save / submit / cancel / void / expert / notice / administrative / execute 一致：
 * @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_DEREGISTER')")。
 * 只读（page / get / archive / print）另含 COLLECTION_SELECT。
 */
export const DEREGISTRATION_WRITE_CODES = 'ADMIN,COLLECTION_ADMIN,COLLECTION_DEREGISTER';

/** PASS 且未 execute 时前端禁「作废」的提示（与后端「已通过的记录不允许作废」对齐）。 */
export const DEREGISTRATION_VOID_BLOCKED_HINT = '已通过不可作废';

/**
 * 与 FlowFormDrawer.canVoidStatus 一致：仅审批中 / 驳回 / 撤销可作废。
 * PASS（含未 execute）与已 execute 一律不可作废。
 */
export function canVoidDeregistration(
  flowStatus?: string | null,
  executedAt?: string | number | null,
): boolean {
  if (executedAt != null && String(executedAt).trim() !== '') return false;
  return ['AUDIT', 'REJECT', 'CANCEL'].includes(String(flowStatus || ''));
}

/** 返回禁用作废原因；可作废时返回 null。 */
export function deregistrationVoidBlockedReason(
  flowStatus?: string | null,
  executedAt?: string | number | null,
): string | null {
  return canVoidDeregistration(flowStatus, executedAt) ? null : DEREGISTRATION_VOID_BLOCKED_HINT;
}
