import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export const restrictedPlanFields: EvidenceField[] = [
  f('incidentIds', '本件全部当前受限事故编号（逐行填写）', 'textarea'),
  f('preparedAt', '专项核查实际形成时间', 'datetime'),
  f('plannedStart', '计划送修及作业开始时间', 'datetime'),
  f('plannedEnd', '本次授权结束及计划归还时间', 'datetime'),
  f('conditionBefore', '当前保存状况、损伤及核查结果', 'textarea'),
  {
    ...f('physicallyPresent', '已实际核实原件在库在场', 'select'),
    options: [
      { label: '是，已据实核实', value: 'YES' },
      { label: '否', value: 'NO' },
    ],
  },
  f('necessity', '事故限制保留时确需修复的理由', 'textarea'),
  f('interventionBoundary', '本次干预范围及与技术方案的对应', 'textarea'),
  f('safetyRequirements', '修复与送修的具体安全措施', 'textarea'),
  f('stopConditions', '出现新风险时的停止与报告安排', 'textarea'),
  f('departmentId', '实际保护修复部门编号'),
  f('conservationUserId', '保护修复核查人员', 'user'),
  f('departmentHeadUserId', '实际提用部门负责人', 'user'),
  f('keeperUserId', '本版方案指定保管负责人', 'user'),
  f('leaderUserId', '本版方案指定主要负责人', 'user'),
  f('securityUserId', '实际安全保卫责任人员', 'user'),
  f('transportPlanFileIds', '送修运输及场所安全方案原件', 'file'),
  f('conditionFileIds', '本次实物保存状况核查影像及原件', 'file'),
  f('signedPlanFileIds', '本次专项风险核查签署原件', 'file'),
];
export const restrictedConfirmFields: EvidenceField[] = [
  f('signedAt', '本人实际确认时间', 'datetime'),
  f('opinion', '本人对完整依据的核查意见', 'textarea'),
  f('fileIds', '本人实际签署原件', 'file'),
];
export const restrictedCancelFields: EvidenceField[] = [
  f('canceledAt', '未实施取消的实际时间', 'datetime'),
  f('reason', '取消原因与后续安排', 'textarea'),
  f('fileIds', '实际取消依据', 'file'),
];
export function restrictedPayload(value: Record<string, any>) {
  const result = { ...value };
  if (typeof result.incidentIds === 'string')
    result.incidentIds = [
      ...new Set(
        result.incidentIds
          .split(/[\n,，]/)
          .map((v: string) => v.trim())
          .filter(Boolean),
      ),
    ];
  if (result.physicallyPresent === 'YES') result.physicallyPresent = true;
  else if (result.physicallyPresent === 'NO') result.physicallyPresent = false;
  return result;
}
