/**
 * 馆内提用提交前置：材质脆弱性评估双签（保护+保管）+ 联合安全定期检查方案三签（保管/提用/安保）。
 * 以及点交时间窗：当前时间须不早于 dossier.expectedOutAt。
 * 不编造接口；状态字段来自既有 fragility / joint-safety detail。
 */

/** 提交前确认文案（无真实状态或状态不明时用）。 */
export const REQUISITION_PREREQUISITE_CONFIRM =
  '提交审批前须先完成：① 清单内每件藏品的材质脆弱性评估，并由保护修复与保管负责人双签；② 本单联合安全定期检查方案，并由保管、提用、安保三方本人确认。未完成时后端会拒绝提交。请确认已办妥，或先到对应入口办理。';

/** 保存草稿前提示（草稿本身不强制前置，但提前告知提交门槛）。 */
export const REQUISITION_PREREQUISITE_DRAFT_HINT =
  '提用草稿可以先保存。之后提交三方审批前，须完成材质脆弱性评估双签（保护+保管）和联合安全定期检查方案三签（保管/提用/安保）。保存后可在本抽屉打开对应办理入口。';

export const REQUISITION_HANDOVER_TOO_EARLY = '未到预计出库时间';

/** 脆弱性评估是否已完成保护+保管双签（取最新已双签的评估；与抽屉 active=有 keeper 一致，且须有 conservation）。 */
export function isFragilityDualSigned(detail: Record<string, any> | null | undefined): boolean {
  const assessments = detail?.assessments;
  if (!Array.isArray(assessments) || !assessments.length) return false;
  return [...assessments].reverse().some((a: any) => !!a?.conservation && !!a?.keeper);
}

/** 联合安全方案是否已完成保管/提用/安保三签（confirmed，或三角色均已 signature）。 */
export function isJointSafetyTripleSigned(detail: Record<string, any> | null | undefined): boolean {
  const plans = detail?.plans;
  if (!Array.isArray(plans) || !plans.length) return false;
  return plans.some((p: any) => {
    if (p?.confirmed === true) return true;
    const sigs = Array.isArray(p?.signatures) ? p.signatures : [];
    const roles = new Set(
      sigs.filter((s: any) => s?.signature).map((s: any) => String(s.role || '').toUpperCase()),
    );
    return roles.has('KEEPER') && roles.has('USE') && roles.has('SECURITY');
  });
}

export type RequisitionPrerequisiteStatus = {
  /** true=已齐；false=明确未齐；null=未能读取状态（权限不足或接口失败） */
  fragilityOk: boolean | null;
  jointSafetyOk: boolean | null;
  missing: string[];
};

/** 根据已拉取的 detail 汇总前置完成情况；传 null 表示该项未读到。 */
export function summarizeRequisitionPrerequisites(
  fragilityDetails: Array<Record<string, any> | null | undefined> | null | undefined,
  jointSafetyDetail: Record<string, any> | null | undefined,
  collectionCount = 0,
): RequisitionPrerequisiteStatus {
  const missing: string[] = [];
  let fragilityOk: boolean | null = null;
  if (fragilityDetails == null) {
    fragilityOk = null;
  } else if (!collectionCount && !fragilityDetails.length) {
    fragilityOk = null;
  } else {
    const list = fragilityDetails.length ? fragilityDetails : Array.from({ length: collectionCount }, () => null);
    const known = list.filter((d) => d != null);
    // 全部读到才判定齐/缺；部分读失败视为状态不明，不误拦。
    if (!list.length || known.length !== list.length) fragilityOk = null;
    else {
      fragilityOk = known.every((d) => isFragilityDualSigned(d));
      if (!fragilityOk) missing.push('材质脆弱性评估双签（保护+保管）');
    }
  }
  let jointSafetyOk: boolean | null = null;
  if (jointSafetyDetail === undefined) {
    jointSafetyOk = null;
  } else if (jointSafetyDetail == null) {
    jointSafetyOk = null;
  } else {
    jointSafetyOk = isJointSafetyTripleSigned(jointSafetyDetail);
    if (!jointSafetyOk) missing.push('联合安全定期检查方案三签（保管/提用/安保）');
  }
  return { fragilityOk, jointSafetyOk, missing };
}

/** 当前时间是否早于预计出库时间（无法解析时视为不早于，不误禁）。 */
export function isBeforeExpectedOutAt(
  dossierOrExpected: { expectedOutAt?: number | string | null } | number | string | null | undefined,
  now = Date.now(),
): boolean {
  const raw =
    dossierOrExpected == null
      ? null
      : typeof dossierOrExpected === 'object'
        ? dossierOrExpected.expectedOutAt
        : dossierOrExpected;
  if (raw == null || raw === '') return false;
  const expected = typeof raw === 'number' ? raw : Number(raw);
  if (!Number.isFinite(expected)) return false;
  return now < expected;
}
