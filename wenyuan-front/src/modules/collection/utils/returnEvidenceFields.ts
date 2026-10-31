import type { EvidenceField } from './evidenceFields';
import { loanYesNo } from './loanEvidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const flag = (key: string, label: string): EvidenceField => ({ ...f(key, label, 'select'), options: loanYesNo });
export const reportTargets = [
  { label: '主管文物行政部门', value: 'COMPETENT' },
  { label: '省级文物行政部门', value: 'PROVINCE' },
  { label: '国务院文物行政部门', value: 'NATIONAL' },
  { label: '公安机关', value: 'POLICE' },
];
export const reportKinds = [
  { label: '情况报告或报案', value: 'INCIDENT' },
  { label: '损毁核查处理结果备案', value: 'RESULT_FILING' },
];
export const securityIncidents = [
  { label: '无此类事件', value: 'NONE' },
  { label: '被盗', value: 'THEFT' },
  { label: '被抢', value: 'ROBBERY' },
  { label: '丢失', value: 'LOSS' },
];
export function returnHeaderFields(loan: boolean): EvidenceField[] {
  return [
    f('inspectedAt', '实际逐件验收时间', 'datetime'),
    { ...f('custodianUserIds', loan ? '实际保管点交人员（至少两人）' : '实际保管点交人员', 'user'), multiple: true },
    ...(loan
      ? [f('returningPersonNames', '借用方实际人员（每行一人，至少两人）', 'textarea')]
      : [{ ...f('returningUserIds', '提用方实际归还人员', 'user'), multiple: true }]),
    f('returningAuthorizationFileIds', '归还方身份及授权依据', 'file'),
    flag('distinctPartiesConfirmed', '已核实双方人员不同'),
    flag('originalDocumentsChecked', '已对照原协议或提用凭证及出库点交单'),
    ...(loan
      ? [
          f('transportStandardReference', '归还实际采用包装运输标准及版本'),
          f('transportStandardApplicability', '标准对本批藏品的适用依据', 'textarea'),
          f('transportCarrierName', '实际承运馆或机构'),
          f('transportCarrierCapabilityFileIds', '承运能力依据原件', 'file'),
          f('transportSupervisorName', '借用归还专人监督人'),
          f('transportSupervisionFileIds', '专人监督记录原件', 'file'),
          flag('packingStandardConfirmed', '已逐件核对包装符合适用标准'),
          f('packingCheckFileIds', '签署逐件包装核验原件', 'file'),
        ]
      : []),
    f('signedHandoverFileIds', '双方签署逐件归还点交单', 'file'),
    f('replacementReason', '补充或更正原因', 'textarea', false),
  ];
}
export function returnItemFields(item: any = {}): EvidenceField[] {
  return [
    {
      ...f('objectKind', '本件法定管理类别', 'select'),
      options: [
        { label: '文物藏品', value: 'RELIC' },
        { label: '科学价值古脊椎动物或古人类化石', value: 'SCIENTIFIC_FOSSIL' },
        { label: '其他藏品', value: 'OTHER' },
      ],
    },
    flag('authenticityMatches', '真实性是否与出库实物一致'),
    f('authenticityMethod', '真实性查验方法及结论依据', 'textarea'),
    flag('conditionMatches', '保存状况是否与出库一致'),
    f('condition', '归还时具体保存状况', 'textarea'),
    flag('textNeedsImages', '文字是否需要影像补充'),
    f('imageFileIds', '本件归还影像', 'file', item.textNeedsImages === true || item.textNeedsImages === 'YES'),
    flag('damaged', '是否发现损毁'),
    { ...f('securityIncident', '是否涉及被盗、被抢或丢失', 'select'), options: securityIncidents },
    f('anomalyDescription', '具体差异或异常情况', 'textarea', returnItemAbnormal(item)),
    f('protectiveMeasures', '待核查期间保管及防护措施', 'textarea', returnItemAbnormal(item)),
  ];
}
export function returnItemAbnormal(item: any) {
  return (
    item.authenticityMatches === false ||
    item.authenticityMatches === 'NO' ||
    item.conditionMatches === false ||
    item.conditionMatches === 'NO' ||
    item.damaged === true ||
    item.damaged === 'YES' ||
    (item.securityIncident && item.securityIncident !== 'NONE')
  );
}
export function returnReportFields(items: any[]): EvidenceField[] {
  return [
    {
      ...f('collectionId', '对应异常藏品', 'select'),
      options: items.map((i) => ({ label: i.content?.formalName || i.collectionId, value: i.collectionId })),
    },
    { ...f('target', '实际报送机关层级', 'select'), options: reportTargets },
    { ...f('kind', '报告性质', 'select'), options: reportKinds },
    f('authorityName', '实际机关全称'),
    f('reportNo', '实际报告编号或报案回执'),
    f('reportedAt', '实际报送时间', 'datetime'),
    f('description', '实际报告情况', 'textarea'),
    f('fileIds', '报告及接收凭据原件', 'file'),
  ];
}
export function returnResolutionFields(items: any[]): EvidenceField[] {
  return [
    {
      ...f('collectionId', '本次完成处理的异常藏品', 'select'),
      options: items.map((i) => ({ label: i.content?.formalName || i.collectionId, value: i.collectionId })),
    },
    {
      ...f('incidentId', '本单本件事故案卷编号'),
      hint: '先在事故处置中完成实际报告、核查处理和本件已生效责任人本人确认，再复制案卷编号',
    },
    f('completedAt', '实际调查处理完成时间', 'datetime'),
    f('cause', '调查查明的原因及处理结论', 'textarea'),
    f('custodianExplanationFileIds', '保管方书面说明', 'file'),
    f('returningExplanationFileIds', '归还方书面说明', 'file'),
    f('investigationFileIds', '原因调查原件', 'file'),
    flag('confirmedAuthentic', '已确认本件原藏品真实性'),
    flag('safeForStorage', '已确认适于安全保管'),
    f('finalCondition', '处理后保存状况', 'textarea'),
    f('resultFileIds', '处理结果原件', 'file'),
    f('keeperUserId', '本件处理责任保管负责人', 'user'),
    f('leaderUserId', '法定代表人或主要负责人', 'user'),
    f('approvedAt', '负责人实际签署时间', 'datetime'),
    f('signedApprovalFileIds', '负责人签署处理结果', 'file'),
    f('reportDetermination', '按数量、价值及情形核实报告程序的结论', 'textarea'),
    f('reportDeterminationFileIds', '报告程序判断依据', 'file'),
    {
      ...f('additionalReportTargets', '依法需要的其他报告机关', 'select', false),
      multiple: true,
      options: reportTargets,
    },
  ];
}
const boolKeys = new Set([
  'distinctPartiesConfirmed',
  'originalDocumentsChecked',
  'authenticityMatches',
  'conditionMatches',
  'textNeedsImages',
  'damaged',
  'confirmedAuthentic',
  'safeForStorage',
  'packingStandardConfirmed',
]);
export function returnEvidencePayload(v: Record<string, any>): Record<string, any> {
  return Object.fromEntries(
    Object.entries(v).map(([k, x]) => [
      k,
      k === 'items'
        ? x.map(returnEvidencePayload)
        : k === 'returningPersonNames' && typeof x === 'string'
          ? [
              ...new Set(
                x
                  .split(/[,，;；\n]/)
                  .map((v) => v.trim())
                  .filter(Boolean),
              ),
            ]
          : boolKeys.has(k)
            ? x === 'YES'
              ? true
              : x === 'NO'
                ? false
                : x
            : x,
    ]),
  );
}
export function returnEvidenceInitial(v: Record<string, any>): Record<string, any> {
  return Object.fromEntries(
    Object.entries(v).map(([k, x]) => [
      k,
      k === 'items'
        ? x.map(returnEvidenceInitial)
        : k === 'returningPersonNames' && Array.isArray(x)
          ? x.join('\n')
          : typeof x === 'boolean'
            ? x
              ? 'YES'
              : 'NO'
            : x,
    ]),
  );
}
export function latestReturnInspectionArchiveId(data: any): string | undefined {
  const latestInspection = [...(data.archives || [])]
    .filter((archive: any) => String(archive.phase || '').startsWith('INSPECTION_'))
    .sort(
      (a: any, b: any) =>
        String(a.archivedAt || '').localeCompare(String(b.archivedAt || '')) ||
        String(a.id || '').localeCompare(String(b.id || ''), undefined, { numeric: true }),
    )
    .at(-1);
  return latestInspection?.id ? String(latestInspection.id) : undefined;
}
export function returnInspectionInitial(data: any) {
  const old = returnEvidenceInitial(data.inspection || {});
  return {
    ...old,
    replacementReason: undefined,
    expectedInspectionArchiveId: latestReturnInspectionArchiveId(data),
    items: (data.sourceSnapshot?.items || []).map(
      (item: any) =>
        old.items?.find((i: any) => i.collectionId === item.collectionId) || {
          collectionId: item.collectionId,
          objectKind:
            item.content?.nature === 'RELIC' || item.content?.nature === 'SCIENTIFIC_FOSSIL'
              ? item.content.nature
              : undefined,
        },
    ),
  };
}
export function returnInspectionError(value: any, ids: string[], loan: boolean, replacing = false): string | undefined {
  const missing = (fields: EvidenceField[], data: any) =>
    fields.find(
      (f) =>
        f.required &&
        (data[f.key] == null || data[f.key] === '' || (Array.isArray(data[f.key]) && !data[f.key].length)),
    );
  const header = missing(returnHeaderFields(loan), value);
  if (header) return `请填写${header.label}`;
  if (!value.originalDocumentsChecked || !value.distinctPartiesConfirmed) return '请核对来源原件和实际双方人员';
  if (loan && value.packingStandardConfirmed !== true) return '借用归还逐件包装核验尚未确认';
  if (new Set(value.custodianUserIds).size < (loan ? 2 : 1) || (loan && new Set(value.returningPersonNames).size < 2))
    return '借用归还双方须分别至少两名不同的实际人员';
  if (!loan && value.custodianUserIds.some((id: string) => value.returningUserIds.includes(id)))
    return '提用方与保管方须由不同实际人员签署';
  if (
    value.items?.length !== ids.length ||
    new Set(value.items.map((i: any) => i.collectionId)).size !== ids.length ||
    value.items.some((i: any) => !ids.includes(i.collectionId))
  )
    return '逐件验收清单应与本次实际接收清单完全一致';
  for (const item of value.items) {
    const field = missing(returnItemFields(item), item);
    if (field) return `请逐件填写${field.label}`;
  }
  if (replacing && !value.expectedInspectionArchiveId) return '请重新打开案卷核对当前点交原件版本';
  if (replacing && !value.replacementReason?.trim()) return '补充或更正点交原件须说明原因';
}
export function returnCaseStatus(data: any) {
  if (data.executionState !== 'EXECUTED') return '待登记实际接收';
  if (!data.inspection) return '历史已归还记录';
  const held =
    String(data.collectionIds || '')
      .split(',')
      .filter(Boolean).length -
    String(data.acceptedCollectionIds || '')
      .split(',')
      .filter(Boolean).length;
  return held ? `已接收，${held} 件待核查` : '已接收并完成验收';
}

export const returnResponsibilityFields: EvidenceField[] = [
  f('handedAt', '实际清点移交时间', 'datetime'),
  f('keeperUserId', '承接保管负责人（登记本人）', 'user'),
  f('directorUserId', '现任主要负责人', 'user'),
  f('reason', '实际任职及责任变动原因', 'textarea'),
  f('actualInventory', '逐件清点、位置、防护及原件移交情况', 'textarea'),
  f('outstandingMatters', '全部未结异常、已采取措施及后续事项', 'textarea'),
  f('appointmentFileIds', '实际任职及职责依据', 'file'),
  f('handoverFileIds', '实际逐件清点移交签署原件', 'file'),
];
export const returnResponsibilitySignatureFields: EvidenceField[] = [
  f('opinion', '本人核对实际清点移交及未结事项的意见', 'textarea'),
  f('fileIds', '本人确认原件', 'file'),
];
