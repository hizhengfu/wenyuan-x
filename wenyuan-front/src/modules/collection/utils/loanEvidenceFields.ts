import { formatLocalDateValue, hasLocalDateValue } from '@/utils/datetime';
import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export const loanYesNo = [
  { label: '是', value: 'YES' },
  { label: '否', value: 'NO' },
];
const flag = (key: string, label: string, required = true): EvidenceField => ({
  ...f(key, label, 'select', required),
  options: loanYesNo,
});
export const loanBorrowerOptions = [
  { label: '国有博物馆', value: 'STATE_MUSEUM' },
  { label: '非国有博物馆', value: 'NON_STATE_MUSEUM' },
  { label: '其他单位', value: 'OTHER_ORGANIZATION' },
  { label: '境外机构', value: 'OVERSEAS_ORGANIZATION' },
];
export const loanPurposeOptions = [
  { label: '陈列展览', value: 'EXHIBITION' },
  { label: '科学研究', value: 'RESEARCH' },
  { label: '其他依法借用', value: 'OTHER_LAWFUL' },
];
export const loanAuthorityOptions = [
  { label: '主管文物行政部门', value: 'COMPETENT' },
  { label: '省级文物行政部门', value: 'PROVINCE' },
  { label: '国务院文物行政部门', value: 'NATIONAL' },
  { label: '国务院', value: 'STATE_COUNCIL' },
];
export function loanDossierFields(d: Record<string, any> = {}): EvidenceField[] {
  return [
    { ...f('borrowerKind', '借用主体类型', 'select'), options: loanBorrowerOptions },
    f('borrowerRegistrationNo', '借用单位登记编号'),
    f('borrowerIdentityFileIds', '单位性质及登记依据', 'file'),
    { ...f('purpose', '借用用途', 'select'), options: loanPurposeOptions },
    f('purposeDescription', '具体目的和必要性', 'textarea'),
    ...(d.purpose === 'EXHIBITION' ? [flag('exhibitionOverseas', '展览实物是否出境')] : []),
    f('keeperUserId', '保管部门负责人', 'user'),
    f('leaderUserId', '法定代表人或主要负责人', 'user'),
    f('supervisionUserId', '本馆在借监管保管员', 'user'),
    f('agreedOutAt', '协议约定出借时间', 'datetime'),
    f('agreedReturnAt', '协议约定归还时间', 'datetime'),
    f('collectiveDecisionAt', '集体研究同意时间', 'datetime'),
    f('collectiveParticipantNames', '实际参会人员（每行一人）', 'textarea'),
    f('collectiveDecisionFileIds', '集体研究同意原件', 'file'),
    f('agreementSignedAt', '实际协议签署时间', 'datetime'),
    f('signedAgreementFileIds', '双方签署借用协议', 'file'),
    f('agreedCollectionListFileIds', '双方确认的完整清单', 'file'),
    f('safetyResponsibilities', '安全责任约定', 'textarea'),
    f('transportRequirements', '运输包装要求', 'textarea'),
    f('transportStandardReference', '适用国家或行业标准及现行版本（如 GB/T 23862-2024）'),
    f('transportStandardApplicability', '本批藏品的标准适用范围与核对依据', 'textarea'),
    f('transportPlanFileIds', '包装运输方案及标准核对签署原件', 'file'),
    f('storageRequirements', '借用保管要求', 'textarea'),
    f('useSite', '实际使用及存放地点'),
    f('carrierName', '承担运输的馆或机构'),
    f('carrierCapabilityFileIds', '运输资信和能力依据', 'file'),
    f('transportSupervisorName', '专职运输监督人员'),
    f('transportSupervisorOrganization', '运输监督人员所属单位'),
    f('transportAssignmentFileIds', '专职运输监督指派原件', 'file'),
    f('firstInspectionOn', '首次双方检查日期', 'date'),
    f('inspectionIntervalDays', '馆方确定的检查间隔天数', 'number'),
    f('inspectionPlanFileIds', '技术指导及监督检查计划', 'file'),
    f('otherLegalBasisFileIds', '其他用途适用法律依据', 'file', false),
    f('overseasProcedureFileIds', '境外机构借用适用程序依据', 'file', false),
    {
      ...f('overseasRequiredApprovalLevels', '境外机构所需审批层级', 'select', false),
      multiple: true,
      options: loanAuthorityOptions,
    },
    ...(d.purpose === 'EXHIBITION' && (d.exhibitionOverseas === true || d.exhibitionOverseas === 'YES')
      ? loanOverseasFields
      : []),
  ];
}
export const loanOverseasFields: EvidenceField[] = [
  f('draftAgreementSignedOn', '报批草签协议日期', 'date'),
  f('draftAgreementFileIds', '报批草签协议草案原件', 'file'),
  f('exhibitionStartsOn', '出境展览开始日期', 'date'),
  f('exhibitionEndsOn', '初次批准展览结束日期', 'date'),
  f('totalRelicExhibits', '所有参展馆文物展品总数', 'number'),
  f('firstLevelExhibits', '全展一级文物件套数', 'number'),
  f('wholeExhibitionListFileIds', '所有参展馆完整展品目录', 'file'),
  f('prohibitedCatalogCheckFileIds', '禁止出境目录核查依据', 'file'),
];
export function loanAssessmentFields(overseas = false): EvidenceField[] {
  return [
    {
      ...f('objectKind', '本件法定管理类别', 'select'),
      options: [
        { label: '文物藏品', value: 'RELIC' },
        { label: '科学价值古脊椎动物或古人类化石', value: 'SCIENTIFIC_FOSSIL' },
        { label: '其他藏品', value: 'OTHER' },
      ],
    },
    f('condition', '当前保存状况', 'textarea'),
    flag('poorCondition', '保存状况是否不佳'),
    flag('transportDamageRisk', '是否易因运输包装受损'),
    f('necessity', '本件借用必要性', 'textarea'),
    f('riskAssessment', '风险评估及防护结论', 'textarea'),
    f('assessmentFileIds', '逐件评估签署原件', 'file'),
    ...(overseas
      ? [
          flag('uniqueOrFragileFirstLevel', '是否属于一级文物孤品或易损品'),
          flag('prohibitedFromExit', '是否列入禁止出境范围'),
          f('domesticExhibitionEvidenceFileIds', '曾在国内正式展出依据', 'file'),
        ]
      : []),
  ];
}
export const loanAdministrativeFields: EvidenceField[] = [
  { ...f('level', '批准或备案机关层级', 'select'), options: loanAuthorityOptions },
  {
    ...f('action', '文书性质', 'select'),
    options: [
      { label: '批准', value: 'APPROVAL' },
      { label: '出借后备案', value: 'FILING' },
    ],
  },
  f('authorityName', '实际行政机关全称'),
  f('documentNo', '实际文号或备案编号'),
  f('submittedOn', '实际向行政机关报送日期', 'date'),
  f('issuedOn', '实际批准或备案确认日期', 'date'),
  f('submissionFileIds', '实际报送及接收凭据', 'file'),
  f('fileIds', '批准或备案原件', 'file'),
];
export const loanHandoverFields: EvidenceField[] = [
  f('reviewedAt', '保管负责人审查指派时间', 'datetime'),
  f('reviewerUserId', '审查并指派的保管负责人', 'user'),
  f('reviewAndAssignmentFileIds', '审查及指派原件', 'file'),
  f('handedAt', '实际点交时间', 'datetime'),
  { ...f('custodianUserIds', '实际保管点交人员（至少两人）', 'user'), multiple: true },
  f('receivingPersonNames', '借用方点交人员（每行一人，至少两人）', 'textarea'),
  f('receivingAuthorizationFileIds', '借用方人员身份授权依据', 'file'),
  flag('distinctPartiesConfirmed', '已核实双方点交人员不同'),
  flag('safetyMeasuresConfirmed', '安全防护措施已落实'),
  flag('siteSafetyConfirmed', '存放场所具备安全条件'),
  f('safetyCheckFileIds', '安全条件核实原件', 'file'),
  f('appliedTransportStandardReference', '实际采用包装运输标准及版本'),
  flag('packingStandardConfirmed', '实际包装已按批准方案和适用标准逐件核对'),
  f('packingCheckFileIds', '逐件包装运输核验签署原件', 'file'),
  f('signedHandoverFileIds', '双方签署的逐件点交单', 'file'),
  f('replacementReason', '补充或更正原因', 'textarea', false),
];
export const loanMonitoringKindOptions = [
  { label: '技术指导', value: 'TECHNICAL_GUIDANCE' },
  { label: '双方安全检查', value: 'SAFETY_CHECK' },
  { label: '督促催还或提前收回', value: 'RECALL' },
  { label: '重大情况实际报告', value: 'MAJOR_REPORT' },
  { label: '问题处理完成', value: 'ISSUE_RESOLVED' },
];
export function loanMonitoringFields(records: any[] = [], items: any[] = []): EvidenceField[] {
  return [
    {
      ...f('collectionIds', '本次实际检查或处理的藏品', 'select'),
      multiple: true,
      options: items.map((i) => ({ value: i.collectionId, label: i.content?.formalName || i.collectionId })),
    },
    { ...f('kind', '记录类型', 'select'), options: loanMonitoringKindOptions },
    f('occurredAt', '实际发生时间', 'datetime'),
    f('lenderUserId', '本馆实际保管监管人员', 'user'),
    f('borrowerRepresentative', '借用单位实际人员'),
    f('description', '指导、检查或处理情况', 'textarea'),
    f('fileIds', '实际原件及签署凭据', 'file'),
    flag('safetyConfirmed', '双方检查确认安全条件', false),
    flag('majorSituation', '是否存在重大情况'),
    {
      ...f('relatedRecordId', '关联的问题记录', 'select', false),
      options: records.map((r) => ({
        value: r.id,
        label: `${loanMonitoringKindOptions.find((k) => k.value === r.content?.kind)?.label || '记录'}：${r.content?.description || r.id}`,
      })),
    },
    f('authorityName', '实际报送的主管文物部门', 'text', false),
    f('reportNo', '实际报告文号或回执', 'text', false),
    f('recallReturnBy', '催还要求归还期限', 'date', false),
  ];
}
const lines = (v: any) =>
  typeof v === 'string'
    ? [
        ...new Set(
          v
            .split(/[,，;；\n]/)
            .map((x: string) => x.trim())
            .filter(Boolean),
        ),
      ]
    : v;
export function loanEvidencePayload(value: Record<string, any>) {
  const result: Record<string, any> = {};
  for (const [key, v] of Object.entries(value)) {
    let converted = [
      'exhibitionOverseas',
      'poorCondition',
      'transportDamageRisk',
      'uniqueOrFragileFirstLevel',
      'prohibitedFromExit',
      'distinctPartiesConfirmed',
      'safetyMeasuresConfirmed',
      'siteSafetyConfirmed',
      'packingStandardConfirmed',
      'safetyConfirmed',
      'majorSituation',
      'textNeedsImages',
    ].includes(key.split('.').at(-1)!)
      ? v === 'YES'
        ? true
        : v === 'NO'
          ? false
          : v
      : v;
    if (['collectiveParticipantNames', 'receivingPersonNames'].includes(key)) converted = lines(converted);
    const parts =
      ([
        'draftAgreementSignedOn',
        'draftAgreementFileIds',
        'exhibitionStartsOn',
        'exhibitionEndsOn',
        'totalRelicExhibits',
        'firstLevelExhibits',
        'wholeExhibitionListFileIds',
        'prohibitedCatalogCheckFileIds',
        'objects',
      ].includes(key)
        ? 'overseasExhibition.'
        : '') + key;
    const pathParts = parts.split('.');
    if (pathParts.some((part) => ['__proto__', 'constructor', 'prototype'].includes(part))) continue;
    let target = result;
    for (const part of pathParts.slice(0, -1)) target = target[part] ||= {};
    target[pathParts.at(-1)!] = converted;
  }
  return result;
}
export function loanEvidenceInitial(value: Record<string, any>) {
  const result: Record<string, any> = {};
  function visit(obj: Record<string, any>, prefix = '') {
    for (const [key, v] of Object.entries(obj)) {
      const path = prefix + key;
      if (v && typeof v === 'object' && !Array.isArray(v)) visit(v, key === 'overseasExhibition' ? '' : path + '.');
      else
        result[path] =
          typeof v === 'boolean'
            ? v
              ? 'YES'
              : 'NO'
            : ['collectiveParticipantNames', 'receivingPersonNames'].includes(key) && Array.isArray(v)
              ? v.join('\n')
              : v;
    }
  }
  visit(value);
  return result;
}
export function loanHandoverInitial(data: Record<string, any>) {
  const old = data.handover?.content || {};
  const result = loanEvidenceInitial(old);
  result.reviewerUserId ||= data.dossier?.keeperUserId;
  result.replacementReason = undefined;
  result.items = (data.baseline?.items || []).map((entry: any) => {
    const previous = old.items?.find((i: any) => i.collectionId === entry.collectionId);
    return {
      ...previous,
      collectionId: entry.collectionId,
      textNeedsImages:
        previous?.textNeedsImages === true ? 'YES' : previous?.textNeedsImages === false ? 'NO' : undefined,
    };
  });
  return result;
}
export function loanHandoverPayload(value: Record<string, any>) {
  const result = loanEvidencePayload(value);
  result.items = (value.items || []).map((item: any) => loanEvidencePayload(item));
  return result;
}
export function loanHandoverError(v: Record<string, any>, ids: string[], replacing = false): string | undefined {
  for (const field of loanHandoverFields.filter((f) => f.required)) {
    const value = v[field.key];
    if (value === undefined || value === null || value === '' || (Array.isArray(value) && !value.length))
      return `请填写${field.label}`;
  }
  if (v.custodianUserIds?.length < 2 || v.receivingPersonNames?.length < 2 || !v.distinctPartiesConfirmed)
    return '借用点交双方各须不少于两名不同的实际人员';
  if (!v.safetyMeasuresConfirmed || !v.siteSafetyConfirmed) return '安全措施和场所条件均须落实后才能出库';
  if (!v.packingStandardConfirmed) return '实际包装须按批准方案和适用标准逐件核对后才能出库';
  if (
    v.items?.length !== ids.length ||
    new Set(v.items.map((i: any) => i.collectionId)).size !== ids.length ||
    v.items.some((i: any) => !ids.includes(i.collectionId))
  )
    return '逐件点交清单与批准清单不一致';
  for (const item of v.items) {
    if (!item.condition?.trim()) return '请逐件记录具体保存现状';
    if (typeof item.textNeedsImages !== 'boolean') return '请逐件核实文字是否需要影像补充';
    if (item.textNeedsImages && !item.imageFileIds) return '文字不能充分说明的部分须补充影像';
  }
  if (replacing && !v.replacementReason?.trim()) return '补充或更正签署原件须说明原因';
}

export function loanObligationSummary(data: Record<string, any>): string {
  const obligations = data.obligations;
  if (obligations?.legacyIncomplete) return '待核实完整借用方案';
  const messages: string[] = [];
  if(!data.outExecuted)messages.push(data.flowStatus==='PASS'?'待签署点交出库':'待完成出借审批');
  for(const filing of obligations?.overseasFilings||[])messages.push(loanOverseasFilingSummary(filing));
  if(!data.outExecuted)return messages.join('；');
  if(obligations?.missingExitClearanceCollectionIds?.length)messages.push(`${obligations.missingExitClearanceCollectionIds.length} 件待补出境手续`);
  if(obligations?.pendingReentryCollectionIds?.length)messages.push(`${obligations.pendingReentryCollectionIds.length} 件待登记复进境结项`);
  if(obligations?.heldForReviewCollectionIds?.length)messages.push(`${obligations.heldForReviewCollectionIds.length} 件已接收待核查`);
  const filings = obligations?.filings || [];
  const overdue = filings.filter((f: any) => f.overdue).length;
  const pending = filings.filter((f: any) => !f.completedOn && !f.overdue).length;
  const late = filings.filter((f: any) => f.onTime === false).length;
  if (overdue) messages.push(`${overdue} 项备案逾期未报`);
  if (pending) messages.push(`${pending} 项待备案`);
  if (late) messages.push(`${late} 项曾逾期报送`);
  if (obligations?.simultaneousFirstLevelFiling === false) messages.push('一级文物两级未同时备案');
  if (obligations?.lateExtensionIds?.length) messages.push(`${obligations.lateExtensionIds.length} 次延期批准晚于原展期`);
  if (obligations?.returnOverdue) messages.push('归还已逾期');
  const checks = (obligations?.inspections || []).filter((c: any) => c.overdue).length;
  if (checks) messages.push(`${checks} 件检查逾期`);
  if (obligations?.pendingMajorReportIds?.length)
    messages.push(`${obligations.pendingMajorReportIds.length} 项重大情况待报告`);
  if (obligations?.unresolvedIssueIds?.length) messages.push(`${obligations.unresolvedIssueIds.length} 项待处理`);
  return messages.join('；') || (data.returned ? '已归还，按档案核对后续事项' : '按计划检查并监督归还');
}

/** 原行政批准、延期批准与补充协议分别保存。 */
export function loanExtensionFields(records: Record<string, any>[] = []): EvidenceField[] {
  return [
    { ...f('originalApprovalId', '原出境展览批准', 'select'), options: records.filter(r=>r.content?.action==='APPROVAL').map(r=>({label:`${r.content.authorityName} · ${r.content.documentNo}`,value:r.id})) },
    f('specialNeed','延期的特殊需要','textarea'), f('authorityName','原审批机关名称'), f('documentNo','本次延期批准文号'),
    f('submittedOn','实际延期申请日期','date'), f('approvedOn','实际延期批准日期','date'),
    f('exhibitionEndsOn','批准后展览结束日期','date'), f('agreedReturnAt','补充协议约定归还时间','datetime'),
    f('agreementSignedOn','双方补充协议签署日期','date'), f('submissionFileIds','延期申请原件','file'),
    f('approvalFileIds','原机关延期批准原件','file'), f('agreementFileIds','双方补充协议原件','file'),
  ];
}

export function loanPortFields(approvals: any[] = [], ports: any[] = [], items: any[] = [], stage = 'EXIT'): EvidenceField[] {
  return [
    {...f('stage', '手续类型', 'select'), options:[{label:'出境放行',value:'EXIT'},{label:'复进境结项',value:'REENTRY'}]},
    {...f('originalApprovalId', '原出境批准记录', 'select'), options:approvals.filter(r=>r.content?.action==='APPROVAL').map(r=>({label:`${r.content.authorityName} ${r.content.documentNo}`,value:r.id}))},
    ...(stage==='REENTRY' ? [{...f('exitRecordId','原出境手续','select'),options:ports.filter(r=>r.content?.request?.stage==='EXIT').map(r=>({label:`${r.content.request.portName} ${r.content.request.certificateNo}`,value:r.id}))}] : []),
    {...f('collectionIds','本次审核藏品','select'),multiple:true,options:items.map(i=>({label:i.content?.formalName||i.collectionId,value:i.collectionId}))},
    f('reviewAgency','文物进出境审核机构','text'),f('reviewedAt','实际审核时间','datetime'),
    f('certificateNo','审核证书号','text'),f('certificateFileIds','审核证书原件','file'),
    ...items.map(i=>f(`identifiers.${i.collectionId}`,`${i.content?.formalName||i.collectionId}：审核登记标识`,'text',false)),
    f('portName','实际口岸','text'),...(stage==='EXIT' ? [f('designatedPortFileIds','指定口岸依据','file')] : []),
    f('customsAt',stage==='REENTRY'?'海关结项时间':'海关放行时间','datetime'),
    f('customsDocumentNo','海关单证号','text'),f('customsFileIds','海关手续原件','file'),
  ];
}
export function loanPortPayload(value: Record<string, any>): Record<string, any> {
  const result=loanEvidencePayload(value);
  result.identifiers=Object.fromEntries((result.collectionIds||[]).map((id:string)=>[id,result.identifiers?.[id]||'']));
  if(result.stage==='EXIT')delete result.exitRecordId;
  if(result.stage==='REENTRY')delete result.designatedPortFileIds;
  return result;
}

export function loanOverseasFilingFields(records:any[]=[],kind='AGREEMENT',editing=false,ending:any=null):EvidenceField[] {
  return [
    {...f('kind','报送事项','select'),options:[{label:'正式协议备案',value:'AGREEMENT'},{label:'展览结项报送',value:'CONCLUSION'}].filter(o=>!editing||ending||o.value==='AGREEMENT')},
    f('authorityName','国家文物行政部门名称','text'),f('submissionNo','实际报送编号','text'),f('submittedOn','实际报送日期','date'),
    f('submissionReceiptFileIds','报送接收凭据','file'),
    f('correctionReason','补充或更正原因','textarea',records.some(r=>r.content?.request?.kind===kind)),
    ...(kind==='CONCLUSION' ? [...(editing?[]:[f('endingRecordId','关联结束记录'),f('actualExhibitionEndedOn','展览实际结束日期','date'),f('endingEvidenceFileIds','实际结束依据','file')]),
      {...f('conclusionFormFileIds','结项备案表','file'),hint:editing?`依据已核实结束日期：${hasLocalDateValue(ending?.content?.request?.actualEndedOn) ? formatLocalDateValue(ending.content.request.actualEndedOn) : '待核实'}`:undefined},f('conclusionReportFileIds','结项报告','file'),f('audiovisualFileIds','展览音像资料','file')] : []),
  ];
}
export function loanOverseasFilingSummary(filing:Record<string,any>):string {
  const label=filing.kind==='AGREEMENT'?'正式协议备案':'展览结项报送';
  const state=filing.submittedOn ? (filing.late?'已逾期报送':'已报送') : filing.overdue?'逾期未报':filing.endDateNeedsVerification?'期限待实际结束事实核实':'待报送';
  return `${label}：${state}${hasLocalDateValue(filing.dueOn)?`，期限 ${formatLocalDateValue(filing.dueOn)}`:''}${filing.lateHistoryIds?.length?'；保留曾迟报记录':''}${filing.filingNeedsReverification?'；结束事实已更正，原报送须重新核对':''}`;
}

export function loanExhibitionEndFields(records:any[]=[]):EvidenceField[] {
  return [f('actualEndedOn','展览实际结束日期','date'),f('evidenceFileIds','结束事实依据','file'),f('correctionReason','更正原因','textarea',records.length>0)];
}
