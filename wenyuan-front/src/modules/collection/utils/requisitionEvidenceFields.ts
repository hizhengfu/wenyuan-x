import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export { requisitionPurposeOptions } from './requisitionPurpose';
export const requisitionYesNo = [
  { label: '是', value: 'YES' },
  { label: '否', value: 'NO' },
];
export const requisitionDossierFields: EvidenceField[] = [
  f('departmentGroupId', '实际提用部门', 'group'),
  f('departmentHeadUserId', '提用部门负责人', 'user'),
  f('keeperUserId', '保管部门负责人', 'user'),
  f('leaderUserId', '法定代表人或主要负责人', 'user'),
  f('useResponsibleUserId', '使用安全责任人', 'user'),
  f('usageMethod', '使用方式', 'textarea'),
  f('purposeDescription', '具体使用目的', 'textarea'),
  f('usageSite', '实际使用地点'),
  f('safetyRequirements', '使用和保管安全要求', 'textarea'),
  f('safetyPlanFileIds', '使用与安全方案原件', 'file'),
  { ...f('transportRequired', '本次是否需要包装运输', 'select'), options: requisitionYesNo },
  f('transportDecisionReason', '包装运输判断依据', 'textarea'),
  f('transportStandardReference', '适用包装运输标准及版本', 'text', false),
  f('transportStandardApplicability', '标准对本批藏品的适用依据', 'textarea', false),
  f('transportCarrierName', '承运馆或机构', 'text', false),
  f('transportCarrierCapabilityFileIds', '承运能力依据原件', 'file', false),
  f('transportPlanFileIds', '签署包装运输方案原件', 'file', false),
  f('expectedOutAt', '预计出库时间', 'datetime'),
  f('expectedReturnAt', '预计归库时间', 'datetime'),
];
export const requisitionHandoverFields: EvidenceField[] = [
  f('reviewedAt', '保管负责人审查时间', 'datetime'),
  f('reviewerUserId', '审查并指派的保管负责人', 'user'),
  f('reviewAndAssignmentFileIds', '审查凭证及指派记录', 'file'),
  f('handedAt', '实际点交时间', 'datetime'),
  { ...f('custodianUserIds', '实际点交保管人员', 'user'), multiple: true },
  { ...f('receivingUserIds', '实际接收提用人员', 'user'), multiple: true },
  { ...f('safetyMeasuresConfirmed', '安全防护措施已落实', 'select'), options: requisitionYesNo },
  { ...f('siteSafetyConfirmed', '存放场所具备安全条件', 'select'), options: requisitionYesNo },
  f('safetyCheckFileIds', '安全条件核实依据', 'file'),
  f('signedHandoverFileIds', '双方签署逐件点交单', 'file'),
  f('appliedTransportStandardReference', '实际采用包装运输标准', 'text', false),
  { ...f('packingStandardConfirmed', '已逐件核对包装符合标准', 'select', false), options: requisitionYesNo },
  f('packingCheckFileIds', '签署逐件包装核验原件', 'file', false),
  f('replacementReason', '补充或更正原件的原因', 'textarea', false),
];
export function requisitionHandoverInitial(data: Record<string, any>) {
  const old = data.handover?.content || {};
  const initial = JSON.parse(JSON.stringify(old));
  initial.reviewerUserId ||= data.dossier?.keeperUserId;
  initial.items = (data.baseline?.items || []).map((entry: any) => {
    const prior = old.items?.find((item: any) => item.collectionId === entry.collectionId);
    return {
      ...prior,
      collectionId: entry.collectionId,
      textNeedsImages: prior?.textNeedsImages === true ? 'YES' : prior?.textNeedsImages === false ? 'NO' : undefined,
    };
  });
  for (const key of ['safetyMeasuresConfirmed', 'siteSafetyConfirmed', 'packingStandardConfirmed'])
    if (typeof initial[key] === 'boolean') initial[key] = initial[key] ? 'YES' : 'NO';
  initial.replacementReason = undefined;
  return initial;
}
/** Keep epoch milliseconds and explicit false values unchanged at the API boundary. */
export function requisitionHandoverPayload(value: Record<string, any>) {
  const flag = (v: any) => (v === 'YES' ? true : v === 'NO' ? false : v);
  return {
    ...value,
    safetyMeasuresConfirmed: flag(value.safetyMeasuresConfirmed),
    siteSafetyConfirmed: flag(value.siteSafetyConfirmed),
    packingStandardConfirmed: flag(value.packingStandardConfirmed),
    items: (value.items || []).map((item: any) => ({ ...item, textNeedsImages: flag(item.textNeedsImages) })),
  };
}
export function requisitionHandoverError(
  value: Record<string, any>,
  expectedIds: string[],
  replacing = false,
  transportRequired = false,
): string | undefined {
  for (const field of requisitionHandoverFields.filter((f) => f.required)) {
    const current = value[field.key];
    if (
      current === null ||
      current === undefined ||
      (typeof current === 'string' && !current.trim()) ||
      (Array.isArray(current) && !current.length)
    )
      return `请填写${field.label}`;
  }
  if (value.safetyMeasuresConfirmed !== true || value.siteSafetyConfirmed !== true)
    return '安全措施或场所条件尚未落实，不能办理出库';
  if (
    transportRequired &&
    (!value.appliedTransportStandardReference?.trim() ||
      value.packingStandardConfirmed !== true ||
      !value.packingCheckFileIds)
  )
    return '实际包装运输须核对批准标准、逐件包装并上传签署原件';
  if (value.custodianUserIds.some((id: string) => value.receivingUserIds.includes(id)))
    return '保管与提用双方须由不同实际人员点交';
  if (
    !expectedIds.length ||
    value.items?.length !== expectedIds.length ||
    new Set(value.items.map((item: any) => item.collectionId)).size !== expectedIds.length ||
    value.items.some((item: any) => !expectedIds.includes(item.collectionId))
  )
    return '逐件点交清单与批准清单不一致，请重新打开本单核对';
  for (const item of value.items) {
    if (!item.condition?.trim()) return '请记录每件藏品的具体保存现状';
    if (typeof item.textNeedsImages !== 'boolean') return '请逐件确认文字是否需要影像补充';
    if (item.textNeedsImages && !item.imageFileIds) return '文字不能充分说明的现状须补充影像';
  }
  if (replacing && !value.replacementReason?.trim()) return '补充或更正原件须说明原因，原版本仍永久保留';
}
