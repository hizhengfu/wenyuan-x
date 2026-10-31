import type { EvidenceField } from './evidenceFields';
import { loanYesNo } from './loanEvidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const flag = (key: string, label: string): EvidenceField => ({ ...f(key, label, 'select'), options: loanYesNo });
export const inventoryMethods = [
  { label: '全面清点', value: 'FULL' },
  { label: '专项盘库', value: 'SPECIAL' },
  { label: '核查抽检', value: 'SAMPLE' },
];
export const inventoryPurposes = [
  { label: '日常或年度清点', value: 'ROUTINE' },
  { label: '主要负责人离任', value: 'DIRECTOR_DEPARTURE' },
  { label: '保管人员离任', value: 'KEEPER_DEPARTURE' },
];
export interface KeeperInventorySource {
  orgId: string;
  warehouseId: string;
  assignmentArchiveId: string;
  leavingUserId: string;
  successorUserIds: string[];
  keeperUserId?: string;
  leaderUserId?: string;
}
export function inventoryPlanFields(value: any = {}): EvidenceField[] {
  return [
    f('year', '实际清点年度', 'number'),
    {
      ...f('method', '清点方式', 'select'),
      options:
        value.purpose === 'KEEPER_DEPARTURE' ? inventoryMethods.filter((m) => m.value !== 'SAMPLE') : inventoryMethods,
    },
    { ...f('purpose', '办理事项', 'select'), options: inventoryPurposes },
    f(
      'scopeDescription',
      value.purpose === 'KEEPER_DEPARTURE' ? '完整保管责任范围及确定依据' : '清点范围与抽检依据',
      'textarea',
    ),
    f('scopeFileIds', '应盘清单及范围依据原件', 'file'),
    f('keeperUserId', '保管部门负责人', 'user'),
    f('leaderUserId', '法定代表人或主要负责人', 'user'),
    ...(value.replacesInventoryBatchId
      ? [
          f('replacesInventoryBatchId', '被替代的原清点批次'),
          f('replanReason', '重新组织全馆清点的实际原因', 'textarea'),
          f('replanFileIds', '重办原因及范围依据原件', 'file'),
        ]
      : []),
    ...(value.keeperStaffingChangeIds?.length
      ? [f('keeperStaffingChangeIds', '历史库管任职变动原件编号', 'textarea')]
      : []),
    ...(value.leadershipDepartureId ? [f('leadershipDepartureId', '关联的负责人离任交接案')] : []),
    ...(value.purpose && value.purpose !== 'ROUTINE'
      ? [
          f('leavingUserId', '实际离任人员', 'user'),
          f('successorUserId', '实际接任人员', 'user'),
          f('leavingAt', '正式离任时间', 'datetime'),
          f('responsibilityFileIds', '任职交接及完整责任范围依据', 'file'),
        ]
      : []),
  ];
}
export function inventoryCheckFields(value: any = {}): EvidenceField[] {
  return [
    f('checkedAt', '实际逐件核验时间', 'datetime'),
    { ...f('checkerUserIds', '实际保管核验人员', 'user'), multiple: true },
    f('actualLocation', '现场实际位置'),
    flag('ledgerMatches', '总登记账是否相符'),
    f('ledgerFileIds', '总登记账核验原件', 'file', value.ledgerMatches === 'YES' || value.ledgerMatches === true),
    flag('objectMatches', '实物是否找到且相符'),
    flag('cardMatches', '编目卡是否相符'),
    f('cardFileIds', '编目卡核验原件', 'file', value.cardMatches === 'YES' || value.cardMatches === true),
    flag('photoMatches', '照片或影像是否相符'),
    f('photoFileIds', '比对照片或影像', 'file', value.photoMatches === 'YES' || value.photoMatches === true),
    f('condition', '实际保存现状', 'textarea'),
    flag('damaged', '是否发现损毁'),
    f('signedCheckFileIds', '实际人员签署的逐件核验记录', 'file'),
    f('findings', '差异、损毁或逾期的具体情况', 'textarea', false),
    f('protectiveMeasures', '发现问题后的即时防护与处置', 'textarea', false),
    f('replacementReason', '补充或更正原因', 'textarea', false),
  ];
}
export function inventoryActionFields(value: any = {}, requiresIncident = false): EvidenceField[] {
  return [
    f('recordedAt', '实际处置记录时间', 'datetime'),
    f('cause', '调查原因及核查进展', 'textarea'),
    f('actions', '已采取的处理措施', 'textarea'),
    f('responsibleUserId', '负责后续核查的保管人员', 'user'),
    flag('resolved', '是否已处理完毕'),
    f('followUpAt', '后续核查时间', 'datetime', value.resolved !== 'YES' && value.resolved !== true),
    flag('major', '是否属于重大情况'),
    f('significanceReason', '重大情况判断依据', 'textarea'),
    f('evidenceFileIds', '调查处理原件', 'file'),
    ...(value.major === 'YES' || value.major === true
      ? [
          f('authorityName', '实际主管文物行政部门'),
          f('reportNo', '实际报告编号'),
          f('reportedAt', '实际报送时间', 'datetime'),
          f('reportFileIds', '重大情况报告与接收凭据', 'file'),
        ]
      : []),
    ...(value.resolved === 'YES' || value.resolved === true
      ? [
          {
            ...f('incidentId', '本批次本件事故案卷编号', 'text', requiresIncident),
            hint: '账物卡照差异或损伤恢复可用前，须完成同一来源案卷及方案责任人本人确认；仅逾期跟进无实物差异时可留空',
          },
          flag('safeForUse', '是否已核实处理并满足再次利用的安全条件'),
          f('keeperUserId', '签署安全结论的保管部门负责人', 'user'),
          f('leaderUserId', '签署安全结论的主要负责人', 'user'),
          f('resolutionApprovedAt', '处理与安全结论实际签署时间', 'datetime'),
          f('signedResolutionFileIds', '负责人签署的处理及安全结论原件', 'file'),
        ]
      : []),
  ];
}
export function inventoryCompletionFields(batch: any, value: any = batch.completion || {}): EvidenceField[] {
  const departure = ['KEEPER_DEPARTURE', 'DIRECTOR_DEPARTURE'].includes(batch.plan?.purpose);
  const delayed = inventoryDepartureLate(batch, value);
  return [
    f('signedAt', '负责人实际签署时间', 'datetime'),
    f('keeperUserId', '实际签署的保管部门负责人', 'user'),
    f('leaderUserId', '实际签署的法定代表人或主要负责人', 'user'),
    f('reportFileIds', '清点结果及责任人签署原件', 'file'),
    ...(departure
      ? [
          f('actualHandoverAt', '实际离任移交时间', 'datetime'),
          f('handoverFileIds', '离任与接任双方签署的移交原件', 'file'),
          f('departureDelayReason', '迟办原因及实际处理情况（迟办必填）', 'textarea', delayed),
          f('departureDelayFileIds', '迟办及处理情况依据原件（迟办必填）', 'file', delayed),
        ]
      : []),
    ...(batch.plan?.purpose === 'DIRECTOR_DEPARTURE' && batch.baseline?.ownership === 'STATE'
      ? [
          f('supervisorAuthority', '实际派员的主管文物行政部门'),
          f('supervisorNames', '实际监督人员（每行一人）', 'textarea'),
          f('supervisionAt', '实际监督清点移交时间', 'datetime'),
          f('appointmentFileIds', '主管部门派员依据', 'file'),
          f('supervisionFileIds', '监督检查原件', 'file'),
        ]
      : []),
  ];
}
export const inventoryAnnualFields: EvidenceField[] = [
  f('year', '报告年度', 'number'),
  f('signedAt', '主要负责人实际签署时间', 'datetime'),
  f('leaderUserId', '实际签署的法定代表人或主要负责人', 'user'),
  f('signedReportFileIds', '签署年度报告原件', 'file'),
  f('authorityName', '实际主管文物行政部门'),
  f('reportNo', '实际报送编号'),
  f('reportedAt', '实际报送时间', 'datetime'),
  f('receiptFileIds', '报送及接收凭据', 'file'),
  f('supplementReason', '补报或更正原因', 'textarea', false),
];
const flags = [
  'ledgerMatches',
  'objectMatches',
  'cardMatches',
  'photoMatches',
  'damaged',
  'resolved',
  'major',
  'safeForUse',
];
export function inventoryPayload(value: Record<string, any>) {
  const result = { ...value };
  for (const key of flags) if (result[key] === 'YES' || result[key] === 'NO') result[key] = result[key] === 'YES';
  if (typeof result.supervisorNames === 'string')
    result.supervisorNames = [
      ...new Set(
        result.supervisorNames
          .split(/\n/)
          .map((v: string) => v.trim())
          .filter(Boolean),
      ),
    ];
  return result;
}
export function inventoryInitial(value: Record<string, any> = {}) {
  const result = { ...value };
  for (const key of flags) if (typeof result[key] === 'boolean') result[key] = result[key] ? 'YES' : 'NO';
  if (Array.isArray(result.supervisorNames)) result.supervisorNames = result.supervisorNames.join('\n');
  return result;
}
export function inventoryAbnormal(value: any) {
  return (
    ['ledgerMatches', 'objectMatches', 'cardMatches', 'photoMatches'].some(
      (k) => value[k] === false || value[k] === 'NO',
    ) ||
    value.damaged === true ||
    value.damaged === 'YES'
  );
}
export function inventoryCoverageReady(c: any) {
  return (
    !!c &&
    !c.missingCategories?.length &&
    !Object.keys(c.missingLocations || {}).length &&
    !c.uncheckedPriorityIds?.length &&
    !c.unclassifiedIds?.length &&
    !c.unhandledIssueIds?.length &&
    !c.unverifiedBatchIds?.length
  );
}
export function inventoryAnnualStatus(year: number, reports: any[], now = Date.now()) {
  if (reports.some((r) => r.evidenceErrors?.length || !r.content)) return '年度报告原件待核对，不能认定报送凭据有效';
  const due = Date.UTC(year, 11, 31, 16);
  if (!reports.length) return now >= due ? '已超过12月底报送期限，待补报' : '待签署报送（12月底前）';
  const first = reports.reduce((a, b) => (a.version < b.version ? a : b)).content;
  return first.reportedAt >= due ? '已补报，首次报送逾期事实保留' : '已登记按期报送凭据';
}

export function inventoryCoverageInitial(value: any) {
  if (!value) return undefined;
  return {
    population: [],
    checkedCollectionIds: [],
    completedBatchIds: [],
    missingCategories: [],
    missingLocations: {},
    uncheckedPriorityIds: [],
    unclassifiedIds: [],
    unhandledIssueIds: [],
    pendingIssueIds: [],
    unverifiedBatchIds: [],
    departureLateBatchIds: [],
    departureScopeReviewBatchIds: [],
    ...value,
  };
}

function inventoryTime(value: unknown): number {
  if (typeof value === 'number') return value;
  if (typeof value === 'string' && value.trim()) return /^\d+$/.test(value) ? Number(value) : Date.parse(value);
  return NaN;
}
export function inventoryDepartureLate(batch: any, value: any): boolean {
  if (!['KEEPER_DEPARTURE', 'DIRECTOR_DEPARTURE'].includes(batch.plan?.purpose)) return false;
  const leaving = inventoryTime(batch.plan?.leavingAt);
  return (
    Number.isFinite(leaving) && [value?.actualHandoverAt, value?.signedAt].some((at) => inventoryTime(at) >= leaving)
  );
}
export function inventoryBatchStatus(batch: any): string {
  if (!batch.evidenceErrors?.length && batch.departureScopeErrors?.length) return '离任范围待补核，原核验及签署保留';
  if (batch.evidenceErrors?.length) return '原件待核对，不能认定已完成';
  if (batch.supersededByBatchId) return '已由后续全馆清点替代，原核验保留';
  if (
    batch.departureStatus === 'COMPLETED_LATE' ||
    (batch.completion && inventoryDepartureLate(batch, batch.completion))
  )
    return '已登记迟办移交，逾期事实保留';
  if (batch.departureStatus === 'COMPLETED_BEFORE_DEPARTURE') return '已登记离任前完成的签署原件';
  if (batch.departureStatus === 'OVERDUE') return '已到离任时间，清点移交待完成';
  return batch.completion ? '已签署清点结果' : batch.plan ? '清点办理中' : '尚未登记实际方案';
}

export function inventoryArchiveSnapshot(archive: any): Record<string, any> | undefined {
  try {
    const value = JSON.parse(archive.snapshot);
    return value && typeof value === 'object' && !Array.isArray(value) ? value : undefined;
  } catch {
    return undefined;
  }
}
