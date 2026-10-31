import type { EvidenceField } from './evidenceFields';
import { institutionItemFields, institutionItemTitles } from './institutionItemFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text'): EvidenceField => ({
  key,
  label,
  type,
  required: true,
});
export const institutionScopeTitles: Record<string, string> = {
  ...institutionItemTitles,
  case: '建立全馆统筹案卷',
  scope: '记录本次全馆清点范围',
  unrecorded: '登记未入系统的清点对象',
  'follow-up': '追加未完成事项及实际处理',
  resolution: '核实实际完成依据',
  'keeper-review': '保管负责人本人核对',
  'representative-review': '依法授权人员本人复核',
};
export const institutionSourceNames: Record<string, string> = {
  COLLECTION: '藏品记录',
  AUXILIARY: '辅助账记录',
  ACQUISITION: '未派生藏品的征集记录',
  DESIGNATED: '指定接收记录',
  UNRECORDED: '现场发现对象',
};
export const institutionScopeStates: Record<string, string> = {
  IN_PROGRESS: '逐项办理及核对中',
  SCOPE_REVIEWED: '本版清册已由保管及依法负责人员复核',
  NEEDS_RECHECK: '范围或依据变化，须重新核对',
};
export const institutionItemStates: Record<string, string> = {
  PENDING: '待入库',
  IN_STORE: '在库',
  IN_USE: '提用中',
  RESTORING: '修复中',
  ON_LOAN: '在借',
  MISSING: '失踪',
  DEREGISTERED: '已退出登记',
  PRE_OUT: '待出库',
  RETURN_HOLD: '退库待核',
  DRAFT: '征集草稿',
  AUDIT: '征集审批中',
  PASS: '征集已批准',
  REJECT: '征集已驳回',
  CANCEL: '征集已撤销',
  VOID: '征集已作废',
  AUXILIARY: '独立辅助账',
  UNRECORDED: '待核实身份和依据',
  TRANSFER_RECORDED: '已有逐件交接记录',
  AWAIT_ASSESSMENT: '待鉴定评估',
  ASSESSMENT: '已有评估原件',
  CUSTODY: '已有实际保管原件',
  REFERRAL: '已报请另行指定',
  REASSIGNMENT: '已有另行指定决定',
};
const resolutions = [
  { value: 'LINKED_ITEM', label: '已本人确认的来源关联及对应业务真实完成' },
  { value: 'OTHER_ITEM_HANDOVER', label: '已按分类方案真实交齐的其他物品' },
  { value: 'INSTITUTION_TRANSFER', label: '已实际执行的终止或合并逐件承接' },
  { value: 'DESIGNATED_REASSIGNMENT', label: '已记录实际结果的另行指定承接' },
  { value: 'ACQUISITION_NOT_COMPLETED', label: '已终止且未取得、未实收的征集申请' },
  { value: 'DRAFT_NOT_ACQUIRED', label: '双人核查未取得、未实收的待登记草稿' },
];
export function institutionScopeFields(
  kind: string,
  context: Record<string, any> = {},
  route?: string,
): EvidenceField[] {
  const signature = f('signedFileIds', '实际签署原件', 'file');
  const operator = f('operatorUserId', '实际办理人本人', 'user');
  const review = [
    f('reviewedAt', '本版清册实际核对时间', 'datetime'),
    f('reviewerUserId', '实际核对人本人', 'user'),
    {
      ...f('allLocationsAndObligationsChecked', '已核实全馆地点、外部保管及所有未完成义务', 'select'),
      options: [
        { value: 'YES', label: '是，已经实际核实' },
        { value: 'NO', label: '否，仍须继续核实' },
      ],
    },
    f('archiveReceiverName', '实际档案承接机构及责任人'),
    f('archiveHandoverFileIds', '原账册、卡片、照片及档案交接原件', 'file'),
    f('unresolvedStatement', '未完成事项核实说明及实际处理结果', 'textarea'),
    signature,
  ];
  const fields: Record<string, EvidenceField[]> = {
    case: [
      { ...f('orgId', '原收藏单位', 'select'), options: context.orgs || [] },
      {
        ...f('eventKind', '机构事项', 'select'),
        options: [
          { value: 'TERMINATION', label: '终止' },
          { value: 'MERGER', label: '合并' },
        ],
      },
      f('sourceMuseumName', '原馆法定名称'),
      f('sourceRegistrationNo', '法人登记标识'),
      f('eventDocumentNo', '实际机构决定文号'),
      f('decidedOn', '实际决定日期', 'date'),
      f('effectiveOn', '决定载明生效日期', 'date'),
      f('eventFileIds', '机构决定原件', 'file'),
      f('nonprofitLegalBasis', '适用的非营利组织法律及章程依据', 'textarea'),
      f('legalBasisFileIds', '相关依据原件', 'file'),
      f('liquidationOrMergerPlanFileIds', '实际清算或合并方案', 'file'),
      f('keeperUserId', '本案保管负责人', 'user'),
      f('authorizedRepresentativeUserId', '依法授权负责人员', 'user'),
      f('representativeAuthorizationFileIds', '人员授权原件', 'file'),
    ],
    scope: [
      f('checkedAt', '本次实际清点时间', 'datetime'),
      operator,
      f('coverageDescription', '已核对地点、在借、修复、暂存及未登记范围', 'textarea'),
      f('inventoryFileIds', '本次实际清点清册原件', 'file'),
      signature,
    ],
    unrecorded: [
      f('recordNo', '本案稳定清点编号'),
      f('name', '实际对象名称'),
      f('quantity', '本条数量', 'number'),
      f('unit', '计量单位'),
      f('actualLocation', '实际所在位置'),
      f('custodianDescription', '实际保管人员及单位'),
      f('foundAt', '实际发现时间', 'datetime'),
      operator,
      f('identification', '身份特征及待核事项', 'textarea'),
      f('photoFileIds', '实物照片原件', 'file'),
      signature,
    ],
    'follow-up': [
      f('recordedAt', '实际处理时间', 'datetime'),
      operator,
      f('responsibleUserId', '后续核查责任人', 'user'),
      f('issue', '具体未完成事项', 'textarea'),
      f('actualMeasures', '已经实际采取的措施', 'textarea'),
      f('nextCheckAt', '后续核查安排', 'datetime'),
      f('evidenceFileIds', '本次处理原件', 'file'),
    ],
    resolution: [
      { ...f('kind', '已经实际完成的业务', 'select'), options: resolutions },
      f('businessId', '同一对象的实际业务记录编号'),
      f('recordedAt', '本次实际完成核实时间', 'datetime'),
      operator,
      f('conclusion', '按原件核实的真实结果', 'textarea'),
      signature,
    ],
    'keeper-review': review,
    'representative-review': review,
  };
  return fields[kind] || institutionItemFields(kind, context, route);
}
export function institutionScopeValues(v: Record<string, any>): Record<string, any> {
  const result = { ...v };
  if ('allLocationsAndObligationsChecked' in result) {
    const value = result.allLocationsAndObligationsChecked;
    if (![true, false, 'YES', 'NO'].includes(value)) throw new Error('请按实际情况选择是否已经核实全部地点及义务');
    result.allLocationsAndObligationsChecked = value === true || value === 'YES';
  }
  return result;
}
export function institutionReviewInitial(keeper?: Record<string, any>, userId?: string): Record<string, any> {
  if (!keeper) return { reviewerUserId: userId };
  // 同一事实需再次核实；实际复核时间、签名及确认不从前一人复制。
  return {
    reviewerUserId: userId,
    archiveReceiverName: keeper.archiveReceiverName,
    archiveHandoverFileIds: keeper.archiveHandoverFileIds,
    unresolvedStatement: keeper.unresolvedStatement,
  };
}
