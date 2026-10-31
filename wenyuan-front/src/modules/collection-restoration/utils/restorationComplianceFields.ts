import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const yesNo = [
  { value: 'YES', label: '是，已依据原件核实' },
  { value: 'NO', label: '否' },
];
export function restorationPlanFields(relic = true, revision = false): EvidenceField[] {
  return [
    f('preparedAt', '方案实际形成时间', 'datetime'),
    f('keeperUserId', '保管部门负责人', 'user'),
    f('leaderUserId', '法定代表人或主要负责人', 'user'),
    f('unchangedPrinciple', '不改变原状的具体要求', 'textarea'),
    f('researchPlan', '全过程科学研究安排', 'textarea'),
    f('preventiveMeasures', '预防保护及后续监测措施', 'textarea'),
    ...(relic
      ? [
          f('qualificationCheckedAt', '机构资质实际核查时间', 'datetime'),
          { ...f('qualificationScopeConfirmed', '本次修复属于资质核定业务范围', 'select'), options: yesNo },
          f('qualificationScopeBasis', '文物材质及事项与资质范围对应依据', 'textarea'),
          f('qualificationReviewFileIds', '资质实际核查原件', 'file'),
        ]
      : []),
    f('siteAddress', '实际修复场所'),
    f('applicableSafetyStandards', '本馆风险等级与场所设施适用标准', 'textarea'),
    f('safetyPlanFileIds', '场所设施安全方案原件', 'file'),
    f('safetyCheckIntervalDays', '方案规定的安全检查间隔（天）', 'number'),
    f('signedPlanFileIds', '本次签署方案原件', 'file'),
    ...(revision
      ? [f('replacementReason', '方案订正原因', 'textarea'), f('replacementFileIds', '本次订正原件', 'file')]
      : []),
  ];
}
export const restorationAdministrativeFields: EvidenceField[] = [
  {
    ...f('level', '实际批准机关层级', 'select'),
    options: [
      { value: 'PROVINCE', label: '省级文物行政部门' },
      { value: 'NATIONAL', label: '国务院文物行政部门' },
    ],
  },
  f('authorityName', '行政机关全称'),
  f('documentNo', '实际批准文号'),
  f('issuedAt', '实际批准日期', 'date'),
  f('fileIds', '本方案行政批文原件', 'file'),
];
export const restorationStartFields: EvidenceField[] = [
  f('requisitionId', '已实际点交出库的送修提用单编号'),
  f('startedAt', '实际开始修复时间', 'datetime'),
  f('operatorUserId', '本馆实际经办人员', 'user'),
  f('siteCheckedAt', '实施场所实际安全核查时间', 'datetime'),
  { ...f('siteSafe', '实施前场所设施安全核查合格', 'select'), options: yesNo },
  f('siteCheckFileIds', '实施前场所安全核查原件', 'file'),
  f('signedStartFileIds', '实际开始修复签署原件', 'file'),
];
export const restorationSafetyFields: EvidenceField[] = [
  f('checkedAt', '实际检查时间', 'datetime'),
  f('checkerUserId', '本馆检查人员', 'user'),
  { ...f('safe', '实际安全结论', 'select'), options: yesNo },
  { ...f('unexpectedDamage', '本次实际发现额外损伤', 'select'), options: yesNo },
  f('findings', '保存状况及场所设施检查发现', 'textarea'),
  f('measures', '实际处理或继续观察措施', 'textarea'),
  f('fileIds', '检查及处置原件', 'file'),
  f('resolvesCheckIds', '本次复查已处置的异常记录编号', 'textarea', false),
];
export const restorationStopFields: EvidenceField[] = [
  f('stoppedAt', '实际停止修复时间', 'datetime'),
  f('operatorUserId', '本馆实际经办人员', 'user'),
  f('reason', '实际停止原因', 'textarea'),
  f('condition', '实际保存现状与未完成情况', 'textarea'),
  f('actualMeasures', '已经采取的即时措施', 'textarea'),
  { ...f('physicallyPresent', '停止时确认实物在场', 'select'), options: yesNo },
  { ...f('unexpectedDamage', '存在实际额外损伤', 'select'), options: yesNo },
  { ...f('safeForCustody', '停止后已核实保管安全', 'select'), options: yesNo },
  f('actualLocation', '实际保管地点或尚未找到的说明'),
  f('custodianUserId', '实际保管责任人员（未查明可留空）', 'user', false),
  f('followUp', '后续调查与保护安排', 'textarea'),
  f('signedStopFileIds', '实际停止与现场保管签署原件', 'file'),
];
export function restorationContinuationFields(organizations: { label: string; value: string }[] = []): EvidenceField[] {
  return [
    f('preparedAt', '本次续作依据实际形成时间', 'datetime'),
    f('reason', '原中止问题、处理结果与本次变更理由', 'textarea'),
    { ...f('physicallyPresent', '本次核实实物在场', 'select'), options: yesNo },
    { ...f('safeForContinuation', '本次核实具备续作安全条件', 'select'), options: yesNo },
    f('actualLocation', '当前实际保管地点'),
    f('custodianUserId', '当前保管责任人员', 'user'),
    f('safetyReviewFileIds', '本次保存状况与安全评估原件', 'file'),
    f('signedFileIds', '本次续作核查及变更签署原件', 'file'),
    f('plan', '新项目修复方案', 'textarea'),
    f('planFileIds', '新方案原件', 'file'),
    f('beforeImages', '本次续作前现状影像', 'file'),
    { ...f('restoreOrgId', '本次承担修复的机构', 'select'), options: organizations },
    f('operatorUserId', '本馆经办人员', 'user'),
    f('reviewerUserId', '复核人员（一级文物必填）', 'user', false),
  ];
}
export const restorationSentStopFields: EvidenceField[] = [
  f('requisitionId', '本项目已实际点交出库的送修提用编号'),
  ...restorationStopFields.map((field) => ({
    ...field,
    label: field.key === 'stoppedAt' ? '实际决定停止启动修复的时间' : field.label,
  })),
];

export const restorationCustodyFields: EvidenceField[] = [
  ...restorationSafetyFields,
  { ...f('physicallyPresent', '本次实际核实实物在场', 'select'), options: yesNo },
  f('actualLocation', '本次实际位置或未找到情况'),
  f('custodianUserId', '本次实际保管责任人员（未查明可留空）', 'user', false),
  f('custodyFileIds', '本次保管复核实际签署原件', 'file'),
];

export const restorationCompletionFields: EvidenceField[] = [
  f('completedAt', '实际竣工时间', 'datetime'),
  f('operatorUserId', '本馆实际经办人员', 'user'),
  f('conditionAndRemainingRisks', '保存状态、修复效果及遗留问题', 'textarea'),
  f('researchRecordFileIds', '全过程研究及实施记录', 'file'),
  f('signedCompletionFileIds', '实际竣工签署原件', 'file'),
  f('afterImageFileIds', '实际竣工影像', 'file'),
];
export function restorationEvidencePayload(v: Record<string, any>) {
  const result = { ...v };
  for (const key of [
    'qualificationScopeConfirmed',
    'siteSafe',
    'safe',
    'physicallyPresent',
    'unexpectedDamage',
    'safeForCustody',
    'safeForContinuation',
  ])
    if (result[key] === 'YES') result[key] = true;
    else if (result[key] === 'NO') result[key] = false;
  if (typeof result.resolvesCheckIds === 'string')
    result.resolvesCheckIds = [
      ...new Set(
        result.resolvesCheckIds
          .split(/[,，;；\n]/)
          .map((x: string) => x.trim())
          .filter(Boolean),
      ),
    ];
  return result;
}


export const restorationResponsibilityFields: EvidenceField[] = [
  f('newKeeperUserId', '续作保管负责人', 'user'),
  f('newLeaderUserId', '续作主要负责人', 'user'),
  f('handedAt', '实际清点移交时间', 'datetime'),
  f('actualInventory', '逐件清点、当前保管及原件移交情况', 'textarea'),
  f('outstandingMatters', '原未完成工序、已处理及全部遗留事项', 'textarea'),
  f('appointmentFileIds', '真实任职及职责依据', 'file'),
  f('handoverFileIds', '实际清点移交签署原件', 'file'),
];
export function restorationResponsibilityForm(value:any) {
  return {...value,newKeeperUserId:value.keeperUserId,newLeaderUserId:value.leaderUserId};
}
