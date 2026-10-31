import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const flag = (key: string, label: string): EvidenceField => ({
  ...f(key, label, 'select'),
  options: [
    { label: '是', value: 'YES' },
    { label: '否', value: 'NO' },
  ],
});
export const incidentWorkStates: Record<string, string> = {
  PLAN: '方案待本人确认',
  APPROVED: '已批准，尚未实际开始',
  STARTED: '现场防护进行中',
  ENDED: '已实际结束，待共同确认',
  COMPLETED: '本次防护已共同确认结束',
  CANCELED: '未实施方案已取消',
};
export const incidentWorkFields: Record<string, EvidenceField[]> = {
  plan: [
    f('preparedAt', '实际方案形成时间', 'datetime'),
    f('plannedStart', '拟开始时间', 'datetime'),
    f('plannedEnd', '允许作业截止时间', 'datetime'),
    f('location', '原地防护的实际场所'),
    f('conditionBefore', '当前实物状态', 'textarea'),
    {
      ...f('methods', '允许的现场防护措施', 'select'),
      multiple: true,
      options: [
        { value: 'SUPPORT', label: '原地支撑' },
        { value: 'PACKAGING', label: '原地包护' },
        { value: 'FIXATION', label: '原地固定' },
        { value: 'ISOLATION', label: '现场隔离' },
        { value: 'ENVIRONMENT_SHIELDING', label: '环境遮护' },
      ],
    },
    f('necessity', '必要性及风险判断', 'textarea'),
    f('interventionBoundary', '明确操作边界与禁止事项', 'textarea'),
    f('procedure', '具体实施方法与步骤', 'textarea'),
    f('safetyRequirements', '现场安全条件及要求', 'textarea'),
    f('stopConditions', '停止操作的条件', 'textarea'),
    f('departmentId', '保护修复责任部门', 'group'),
    f('conservationUserId', '保护修复责任人员', 'user'),
    f('departmentHeadUserId', '该部门负责人', 'user'),
    f('keeperUserId', '保管部门负责人', 'user'),
    f('leaderUserId', '法定代表人或主要负责人', 'user'),
    f('securityUserId', '现场安全责任人员', 'user'),
    f('siteAndAssignmentFileIds', '场所条件及人员指派原件', 'file'),
    f('signedPlanFileIds', '完整现场防护方案原件', 'file'),
  ],
  confirm: [
    f('signedAt', '本人实际签署时间', 'datetime'),
    f('opinion', '本人核对原件的意见', 'textarea'),
    f('fileIds', '本人签署原件', 'file'),
  ],
  start: [
    f('startedAt', '实际开始时间', 'datetime'),
    f('location', '实际原地作业场所'),
    f('conservationUserId', '实际保护修复人员', 'user'),
    f('custodianUserId', '在场实际保管员', 'user'),
    f('securityUserId', '实际现场安全责任人员', 'user'),
    flag('physicallyPresent', '已确认本件实物实际在场'),
    flag('siteSafe', '场所具备本次防护安全条件'),
    flag('noRelocation', '本次作业不改变存放场所'),
    flag('noRestorationOrSampling', '本次作业不实施修复或取样'),
    f('condition', '开始时实际现状', 'textarea'),
    f('actualProtection', '现场已落实的保护措施', 'textarea'),
    f('signedStartFileIds', '现场人员签署的实际开始原件', 'file'),
  ],
  observation: [
    f('observedAt', '实际观察时间', 'datetime'),
    f('observerUserId', '实际观察人员', 'user'),
    f('condition', '观察到的真实现状', 'textarea'),
    flag('unsafe', '是否发现不安全情况'),
    flag('unexpectedDamage', '是否出现额外损伤'),
    f('actualMeasures', '已经采取的现场保护措施', 'textarea'),
    f('fileIds', '实际观察和措施原件', 'file'),
  ],
  end: [
    f('endedAt', '实际停止或结束时间', 'datetime'),
    flag('workPerformed', '是否实际实施了方案措施'),
    f('actualMeasures', '实际实施内容或停止情况', 'textarea'),
    f('conditionAfter', '结束时实物真实现状', 'textarea'),
    flag('withinApprovedScope', '实际作业是否均在批准边界和时段内'),
    flag('unexpectedDamage', '是否出现额外损伤'),
    f('stopReason', '实际完成或停止原因', 'textarea'),
    f('followUp', '后续保管、观察及报告安排', 'textarea'),
    f('signedEndFileIds', '实际结束和现状原件', 'file'),
  ],
  cancel: [
    f('canceledAt', '未实施取消的实际时间', 'datetime'),
    f('reason', '未实施并取消的实际原因', 'textarea'),
    f('fileIds', '取消依据原件', 'file'),
  ],
};
const flags = new Set([
  'physicallyPresent',
  'siteSafe',
  'noRelocation',
  'noRestorationOrSampling',
  'unsafe',
  'unexpectedDamage',
  'workPerformed',
  'withinApprovedScope',
]);
export function incidentWorkValues(v: Record<string, any>) {
  return Object.fromEntries(
    Object.entries(v).map(([k, x]) => [k, flags.has(k) ? (x === 'YES' ? true : x === 'NO' ? false : x) : x]),
  );
}
