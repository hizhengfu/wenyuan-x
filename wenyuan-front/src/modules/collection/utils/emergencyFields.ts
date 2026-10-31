import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const yes = (key: string, label: string, required = true): EvidenceField => ({
  ...f(key, label, 'select', required),
  options: [
    { label: '是', value: 'YES' },
    { label: '否', value: 'NO' },
  ],
});
const multi = (key: string, label: string, required = true): EvidenceField => ({
  ...f(key, label, 'select', required),
  multiple: true,
  options: [],
});
export const emergencyScenarios: Record<string, string> = {
  FIRE: '火灾',
  FLOOD: '水灾',
  EARTHQUAKE: '地震',
  THEFT: '被盗',
  ROBBERY: '被抢',
  LOSS: '丢失',
  DAMAGE: '损毁',
  ENVIRONMENT: '环境突变',
  OTHER: '其他风险',
};
export const emergencyStatuses: Record<string, string> = {
  MISSING_PLAN: '尚无已生效预案',
  MISSING_SCENARIO: '本情景演练安排待补齐',
  PENDING_CONFIRMATION: '实际预案待本人核对',
  OVERDUE: '演练已到期或缺少有效实际记录',
  SCHEDULED: '按实际安排跟踪',
};
export const emergencyRoles: Record<string, string> = {
  KEEPER: '保管负责人',
  SECURITY: '安全保卫负责人',
  DIRECTOR: '主要负责人',
  EVALUATOR: '实际评估人员',
};
const scenarioOptions = Object.entries(emergencyScenarios).map(([value, label]) => ({ value, label }));
export const emergencyFields: Record<string, EvidenceField[]> = {
  plan: [
    f('title', '实际预案名称'),
    f('documentNo', '实际印发文号'),
    f('issuedAt', '实际印发时间', 'datetime'),
    f('effectiveAt', '正式生效时间', 'datetime'),
    f('keeperUserId', '保管部门负责人', 'user'),
    f('securityUserId', '安全保卫责任人员', 'user'),
    f('directorUserId', '法定代表人或主要负责人', 'user'),
    f('commandAndDuties', '指挥体系、部门及人员职责', 'textarea'),
    f('warningAndActivation', '风险预警、启动条件和程序', 'textarea'),
    f('emergencyContacts', '真实应急联络及备用渠道', 'textarea'),
    f('siteProtectionAndTransfer', '现场、实物防护及必要安全转移', 'textarea'),
    f('resourcesAndSupport', '物资、队伍和外部支援', 'textarea'),
    f('firstGradeDamageReporting', '一级文物损毁向国家文物行政部门核查处理程序', 'textarea'),
    f('otherRelicDamageReporting', '其他文物损毁报省级核查及结果报国家备案程序', 'textarea'),
    f('theftRobberyLossReporting', '被盗被抢丢失立即公安报案及主管部门报告程序', 'textarea'),
    f('recoveryAndReview', '恢复、复盘及预案修订要求', 'textarea'),
    {
      ...f('scenarios', '各实际风险情景及演练周期', 'records'),
      children: [
        { ...f('scenario', '情景', 'select'), options: scenarioOptions },
        f('objectives', '实际演练目标及方法', 'textarea'),
        f('drillIntervalDays', '按风险评估周期（天）', 'number'),
        f('firstDueAt', '首次演练期限', 'datetime'),
      ],
    },
    f('originalFileIds', '实际印发预案原件', 'file'),
    f('replacementReason', '修订原因（新版必填）', 'textarea', false),
    f('replacementFileIds', '实际修订原件（新版必填）', 'file', false),
  ],
  sign: [f('opinion', '本人核对原件、履职或评估意见', 'textarea'), f('fileIds', '本人签署原件', 'file')],
  drill: [
    f('planId', '真实开始时已生效的预案', 'select'),
    f('title', '实际演练名称'),
    {
      ...f('mode', '演练方式', 'select'),
      options: [
        { value: 'TABLETOP', label: '桌面推演' },
        { value: 'FIELD', label: '现场演练' },
      ],
    },
    f('startedAt', '实际开始时间', 'datetime'),
    f('endedAt', '实际结束时间', 'datetime'),
    { ...multi('scenarios', '实际演练情景'), options: scenarioOptions },
    f('conductorUserId', '实际组织实施人员（本人）', 'user'),
    f('evaluatorUserId', '实际参加评估的保管或安全保卫责任人员', 'user'),
    { ...f('participantUserIds', '真实参加的本馆人员', 'user'), multiple: true },
    f('externalParticipants', '实际外部参加人员及单位', 'textarea', false),
    f('actualLocation', '实际演练地点'),
    f('actualActions', '实际实施动作与经过', 'textarea'),
    f('communicationRehearsal', '模拟报警、报告及通信演练情况', 'textarea'),
    f('actualOutcome', '实际演练结果和评估', 'textarea'),
    yes('involvesOriginals', '是否涉及藏品原件现场操作'),
    yes('originalUseBasisVerified', '涉及原件时是否已核实实际手续', false),
    f('originalOperations', '涉及原件的实际操作与既有业务依据', 'textarea', false),
    f('originalOperationFileIds', '涉及原件的实际操作及手续原件', 'file', false),
    multi('requisitionIds', '关联已真实出库的提用（如有）', false),
    yes('hasIssues', '是否发现实际问题'),
    {
      ...f('issues', '实际问题及整改安排', 'records', false),
      children: [
        f('title', '问题'),
        f('findings', '实际发现', 'textarea'),
        f('ownerUserId', '实际整改责任人', 'user'),
        f('dueAt', '整改期限', 'datetime'),
        f('requiredActions', '要求实际采取的措施', 'textarea'),
        yes('requiresPlanRevision', '是否要求实际修订预案'),
      ],
    },
    yes('hasRealIncident', '期间是否发生或发现真实事故'),
    multi('actualIncidentIds', '另行登记的真实事故（如有）', false),
    f('attendanceFileIds', '实际签到及参加凭据', 'file'),
    f('signedReportFileIds', '真实演练报告原件', 'file'),
  ],
  improvement: [
    f('completedAt', '实际整改完成时间', 'datetime'),
    f('ownerUserId', '本项整改责任人（本人）', 'user'),
    f('verifierUserId', '另一位预案责任人员独立复核', 'user'),
    f('actualActions', '实际完成的整改', 'textarea'),
    f('result', '真实结果与复测依据', 'textarea'),
    f('fileIds', '实际整改原件', 'file'),
    f('revisedPlanId', '已实际印发、生效并核对的新版预案', 'select', false),
    f('replacementReason', '继续整改或补充的实际原因', 'textarea', false),
  ],
  review: [
    yes('accepted', '独立复核是否通过'),
    f('opinion', '实际复核结果及意见', 'textarea'),
    f('fileIds', '本人实际复核原件', 'file'),
  ],
};
export const emergencyTitles: Record<string, string> = {
  plan: '登记实际印发预案',
  signPlan: '本人核对预案原件',
  drill: '登记实际演练与评估',
  signDrill: '本人核对本版演练结果',
  revision: '订正本次演练报告并追加新发现',
  improvement: '记录本项实际整改',
  review: '本人独立复核整改',
};
export function emergencyValues(value: Record<string, any>) {
  const bool = new Set([
    'involvesOriginals',
    'originalUseBasisVerified',
    'hasIssues',
    'hasRealIncident',
    'requiresPlanRevision',
    'accepted',
  ]);
  const convert = (v: Record<string, any>): Record<string, any> =>
    Object.fromEntries(
      Object.entries(v).map(([key, val]) => [
        key,
        bool.has(key) && ['YES', 'NO'].includes(val)
          ? val === 'YES'
          : ['issues', 'additionalIssues'].includes(key) && Array.isArray(val)
            ? val.map(convert)
            : val,
      ]),
    );
  return convert(value);
}

export function emergencyReportInitial(value: Record<string, any>) {
  const result = { ...value };
  for (const key of ['involvesOriginals', 'originalUseBasisVerified', 'hasRealIncident']) {
    if (typeof result[key] === 'boolean') result[key] = result[key] ? 'YES' : 'NO';
  }
  delete result.issues;
  delete result.hasIssues;
  delete result.conductorUserId;
  return result;
}
export const emergencyRevisionFields: EvidenceField[] = [
  {
    ...emergencyFields.drill.find((f) => f.key === 'issues')!,
    key: 'additionalIssues',
    label: '本次追加的新发现及整改安排',
  },
  f('revisionReason', '实际订正原因及变化说明', 'textarea'),
  f('revisionFileIds', '本次订正说明和依据原件', 'file'),
];
