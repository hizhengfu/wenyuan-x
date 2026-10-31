import type { EvidenceField } from './evidenceFields';
export const educationKinds = [
  { value: 'POST_TRAINING', label: '岗位培训' },
  { value: 'CONTINUING_EDUCATION', label: '业务技能继续教育' },
];
export const educationStates: Record<string, string> = {
  PLANNED: '已登记需求，待参加',
  RECORDED: '已登记参加，待核实',
  VERIFIED: '已核实满足本项需求',
  NEEDS_SUPPLEMENT: '本项需求尚未满足',
  WITHDRAWN: '需求已终止，未记作完成',
  EVIDENCE_REVIEW_REQUIRED: '原件待核对',
};
export const yesNo = [
  { value: 'YES', label: '是' },
  { value: 'NO', label: '否' },
];
export const educationPlanFields: EvidenceField[] = [
  { key: 'kind', label: '教育类型', type: 'select', options: educationKinds, required: true },
  { key: 'title', label: '岗位教育需求名称', required: true },
  { key: 'dutyScope', label: '实际岗位与责任范围', type: 'textarea', required: true },
  { key: 'skillsNeeded', label: '岗位所需业务技能', type: 'textarea', required: true },
  { key: 'identifiedAt', label: '实际需求核实时间', type: 'datetime', required: true },
  { key: 'needsFileIds', label: '岗位技能需求依据', type: 'file', required: true },
  { key: 'dueAt', label: '馆方明确的完成期限', type: 'datetime' },
  { key: 'dueBasisFileIds', label: '期限依据（指定期限时必填）', type: 'file' },
];
export const educationParticipationFields: EvidenceField[] = [
  { key: 'provider', label: '实际培训或教育单位', required: true },
  { key: 'courseTitle', label: '实际课程或活动名称', required: true },
  { key: 'startedAt', label: '实际参加开始时间', type: 'datetime', required: true },
  { key: 'endedAt', label: '实际参加结束时间', type: 'datetime', required: true },
  { key: 'actualMinutes', label: '有依据的实际分钟数（可不填）', type: 'number' },
  { key: 'skillsCovered', label: '实际学习与技能内容', type: 'textarea', required: true },
  {
    key: 'outcome',
    label: '实际完成情况',
    type: 'select',
    required: true,
    options: [
      { value: 'ATTENDED', label: '已参加，尚不能确认完成' },
      { value: 'COMPLETED', label: '已完成该课程或活动' },
      { value: 'NOT_COMPLETED', label: '未完成' },
    ],
  },
  { key: 'evidenceFileIds', label: '参加、结业及考核原件', type: 'file', required: true },
  { key: 'assessment', label: '实际考核或补充说明', type: 'textarea' },
];
export const educationCorrectionFields: EvidenceField[] = [
  { key: 'reason', label: '补充、更正或终止原因', type: 'textarea', required: true },
  { key: 'fileIds', label: '本次办理依据原件', type: 'file', required: true },
];
export const educationReviewFields: EvidenceField[] = [
  { key: 'meetsNeed', label: '本人核实满足本项岗位需求', type: 'select', options: yesNo, required: true },
  { key: 'opinion', label: '本人核实意见', type: 'textarea', required: true },
  { key: 'fileIds', label: '实际核实签署原件', type: 'file', required: true },
];
export function educationPick(value: Record<string, any>, fields: EvidenceField[]) {
  return Object.fromEntries(fields.map((f) => [f.key, value[f.key] ?? null]));
}
