import type { EvidenceField } from './evidenceFields';
export const managementRuleTopics = [
  ['ACQUISITION', '藏品取得'],
  ['IDENTIFICATION', '鉴定'],
  ['GRADING', '定级'],
  ['GENERAL_LEDGER', '藏品总登记账'],
  ['ARCHIVES', '藏品档案'],
  ['WAREHOUSE', '库房管理'],
  ['MOVEMENT', '出入库'],
  ['INVENTORY', '清点'],
  ['EXIT', '退出'],
  ['RESTORATION', '修复'],
  ['REPRODUCTION', '复制'],
  ['RUBBING', '拓印'],
] as const;
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export const managementRulesFields = (revision = false): EvidenceField[] => [
  f('title', '实际制度汇编名称'),
  f('documentNo', '实际制度文号'),
  f('issuedAt', '实际印发时间', 'datetime'),
  f('effectiveOn', '文书规定的施行日期', 'date'),
  f('responsibleUserId', '牵头的本馆主要负责人', 'user'),
  f('originalFileIds', '实际制度完整原件', 'file'),
  f('workflowFileIds', '配套内部流程原件', 'file'),
  ...managementRuleTopics.map(([key, label]) => ({
    ...f(`topic_${key}`, `${label}：原件章节或条款`, 'text', false),
    hint: '按实际制度填写，未覆盖时留空；留空事项继续显示缺口',
  })),
  f('replacementReason', '本次修订原因', 'textarea', revision),
  f('replacementFileIds', '本次实际修订依据原件', 'file', revision),
];
export const rulesSubmissionFields = [
  f('authorityName', '主管文物行政部门'),
  f('reportNo', '实际报送编号'),
  f('submittedAt', '实际报送时间', 'datetime'),
  f('submissionFileIds', '实际报送凭据', 'file'),
];
export const rulesFilingFields = [
  f('authorityName', '主管文物行政部门'),
  f('filingNo', '实际备案编号'),
  f('filedAt', '实际备案回执时间', 'datetime'),
  f('receiptFileIds', '实际备案回执原件', 'file'),
];
export const managementRulesStatus = (r: any) =>
  r.filing ? '已留存实际备案回执' : r.submission ? '已实际报送，待备案回执' : '已印发留档，尚无实际报送凭据';
export function rulesPayload(value: Record<string, any>) {
  const payload: Record<string, any> = { ...value },
    topicReferences: Record<string, string> = {};
  for (const [key] of managementRuleTopics) {
    const name = `topic_${key}`;
    if (value[name]?.trim()) topicReferences[key] = value[name].trim();
    delete payload[name];
  }
  return { ...payload, topicReferences };
}
