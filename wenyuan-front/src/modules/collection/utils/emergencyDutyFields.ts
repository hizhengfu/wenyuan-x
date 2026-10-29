import type { EvidenceField } from './evidenceFields';
export const emergencyDutyRoles: Record<string, string> = {
  KEEPER: '保管负责人原件核对',
  SECURITY: '安全保卫负责人原件核对',
  DIRECTOR: '主要负责人原件核对',
  RECORDER: '演练报告后续登记',
  EVALUATOR: '演练评估原件核对',
  OWNER: '问题整改及后续核对',
  REVIEWER: '整改独立复核',
};
export const emergencyDutyQualifications: Record<string, string> = {
  MEMBER: '本馆实际承办人员',
  KEEPER: '保管部门负责人',
  SECURITY: '安全保卫负责人',
  DIRECTOR: '法定代表人或主要负责人',
};
export const emergencyDutyFields: EvidenceField[] = [
  { key: 'toUserId', label: '接任人员', type: 'user', required: true },
  {
    key: 'qualification',
    label: '接任职责的实际岗位资格',
    type: 'select',
    required: true,
    options: Object.entries(emergencyDutyQualifications).map(([value, label]) => ({ value, label })),
  },
  { key: 'appointedAt', label: '实际责任指定时间', type: 'datetime', required: true },
  { key: 'effectiveAt', label: '实际指定生效时间', type: 'datetime', required: true },
  { key: 'reason', label: '实际调整原因及承接事项', type: 'textarea', required: true },
  { key: 'fileIds', label: '实际指定及职责安排原件', type: 'file', required: true },
  {
    key: 'predecessorUnavailable',
    label: '原任是否确实无法本人交接',
    type: 'select',
    required: true,
    options: [
      { value: 'YES', label: '是' },
      { value: 'NO', label: '否' },
    ],
  },
  { key: 'unavailableReason', label: '确实无法交接的原因（选择是时必填）', type: 'textarea' },
  { key: 'unavailableFileIds', label: '无法交接的实际证明原件（选择是时必填）', type: 'file' },
];
export function emergencyDutyValues(value: Record<string, any>) {
  const v = { ...value };
  if (v.predecessorUnavailable === 'YES' || v.predecessorUnavailable === 'NO')
    v.predecessorUnavailable = v.predecessorUnavailable === 'YES';
  return v;
}
