import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text'): EvidenceField => ({
  key,
  label,
  type,
  required: true,
});
export const jointSafetyStatuses: Record<string, string> = {
  MISSING_SOURCE: '来源及职责待核实',
  MISSING_PLAN: '共同检查方案待确认',
  STALE_PLAN: '来源变化，方案待重核',
  PLANNING: '出库前已安排',
  OVERDUE: '已到期或待初次核查',
  CURRENT: '尚未到期',
  AWAITING_DIRECTOR: '问题待报告或负责人处理',
  RECEIVED: '已实际接收归还',
  ORIGINAL_ERROR: '原件异常，须核查',
};
export const jointSafetyRoles: Record<string, string> = {
  KEEPER: '保管部门',
  USE: '提用部门',
  SECURITY: '安全保卫部门',
  DIRECTOR: '主要负责人',
};
export const jointSafetyFields: Record<string, EvidenceField[]> = {
  recall: [
    { ...f('collectionIds', '要求提前归库的藏品', 'select'), multiple: true },
    f('issuedAt', '实际发出归库要求的时间', 'datetime'),
    f('returnBy', '要求归库期限（早于原计划）', 'datetime'),
    {
      ...f('reason', '安全原因', 'select'),
      options: [
        { value: 'NO_PROTECTION', label: '未采取安全防护措施' },
        { value: 'UNSAFE_SITE', label: '存放场所不具备安全防护条件' },
      ],
    },
    f('safetyFindings', '实际安全问题及依据', 'textarea'),
    f('protectiveMeasures', '停止使用、防护和安全归库措施', 'textarea'),
    f('notification', '向提用责任人员实际通知的情况', 'textarea'),
    f('fileIds', '安全依据及归库通知原件', 'file'),
  ],
  custody: [
    f('handedAt', '实际在用责任交接时间', 'datetime'),
    f('reason', '实际岗位变动及本次承接原因', 'textarea'),
    f('actualHandover', '完整责任范围、实物位置及实际交接情况', 'textarea'),
    f('outstandingMatters', '待检查、异常、报告及其他未结事项和承接安排', 'textarea'),
    { ...f('checkIds', '本次尚在用每件藏品的实际现状核对', 'select'), multiple: true },
    f('keeperUserId', '新任保管部门负责人（登记本人）', 'user'),
    f('useUserId', '承接的提用责任人员', 'user'),
    f('directorUserId', '现任主要负责人', 'user'),
    f('appointmentFileIds', '实际任职及职责依据', 'file'),
    f('handoverFileIds', '实际逐件清点、交接签署原件', 'file'),
  ],
  plan: [
    f('preparedAt', '实际方案制定时间', 'datetime'),
    f('inspectionIntervalDays', '按本次实际风险确定周期（天）', 'number'),
    f('firstDueAt', '首次检查期限', 'datetime'),
    f('keeperUserId', '本次提用保管负责人', 'user'),
    f('useUserId', '本次提用使用责任人员', 'user'),
    f('securityUserId', '安全保卫责任人员', 'user'),
    f('directorUserId', '本次提用主要负责人', 'user'),
    f('scopeAndMethod', '逐件范围与实际检查方法', 'textarea'),
    f('environmentalAndSecurityChecks', '保存环境、场所防护与值守检查', 'textarea'),
    f('measuresAndReporting', '发现问题的处理和报告安排', 'textarea'),
    f('planFileIds', '实际共同方案原件', 'file'),
  ],
  round: [
    f('planId', '本轮实际开始时生效的方案', 'select'),
    f('startedAt', '实际检查开始时间', 'datetime'),
    f('endedAt', '实际检查结束时间', 'datetime'),
    { ...f('checkIds', '本轮实际逐件现状原件', 'select'), multiple: true },
    f('actualAreas', '实际检查场所与范围', 'textarea'),
    f('jointFindings', '共同检查结果及发现的问题', 'textarea'),
    f('actualMeasures', '已采取措施及后续安排', 'textarea'),
    f('signedFileIds', '共同检查原件', 'file'),
  ],
  sign: [f('opinion', '本人核对与实际履职意见', 'textarea'), f('fileIds', '本人确认原件', 'file')],
  reportHandover: [
    f('handedAt', '实际向新任负责人移交报告的时间', 'datetime'),
    f('keeperUserId', '现任保管负责人（登记本人）', 'user'),
    f('directorUserId', '接收报告的现任主要负责人', 'user'),
    f('reason', '原报告需移交新任人员的实际原因', 'textarea'),
    f('outstandingMatters', '原问题、已采取措施及全部待处理事项', 'textarea'),
    f('appointmentFileIds', '现任岗位及职责依据', 'file'),
    f('handoverFileIds', '实际问题报告移交原件', 'file'),
  ],
  report: [
    f('reportedAt', '实际向主要负责人报告时间', 'datetime'),
    f('reporterUserId', '实际报告人（本人）', 'user'),
    f('directorUserId', '接收报告的主要负责人', 'user'),
    f('content', '本轮实际发现和报告内容', 'textarea'),
    f('immediateMeasures', '已采取的保护和处置措施', 'textarea'),
    f('signedFileIds', '实际报告原件', 'file'),
  ],
};
jointSafetyFields.currentReportBasis = [
  f('appointmentFileIds', '现任主要负责人实际岗位及职责依据', 'file'),
  f('changeReason', '原方案负责人变化及向现任人员报告的实际依据', 'textarea'),
];
jointSafetyFields.reportCurrent = [...jointSafetyFields.currentReportBasis, ...jointSafetyFields.report];
export const jointSafetyTitles: Record<string, string> = {
  recall: '保管负责人登记提前归库要求',
  custody: '登记在用责任承接',
  signCustody: '本人确认在用责任承接',
  plan: '制定共同检查方案',
  round: '登记实际共同检查',
  signPlan: '本人确认共同检查方案',
  signRound: '本人确认实际参与检查',
  report: '登记实际向主要负责人报告',
  reportCurrent: '向现任主要负责人如实报告',
  reportHandover: '登记问题报告移交',
  signReportHandover: '本人确认报告移交及收悉处理',
  director: '主要负责人本人收悉及处理',
};
