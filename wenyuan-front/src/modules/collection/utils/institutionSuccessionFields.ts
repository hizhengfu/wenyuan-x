import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key: `succession.${key}`,
  label,
  type,
  required,
});
const yesNo = [
  { value: 'YES', label: '是' },
  { value: 'NO', label: '否' },
];
export function institutionSuccessionFields(): EvidenceField[] {
  return [
    {
      ...f('eventKind', '机构事项', 'select'),
      options: [
        { value: 'TERMINATION', label: '终止' },
        { value: 'MERGER', label: '合并' },
      ],
    },
    f('sourceMuseumName', '原博物馆正式名称'),
    f('sourceRegistrationNo', '原单位法定登记标识'),
    f('eventDocumentNo', '终止或合并决定文号'),
    f('decidedOn', '实际决定日期', 'date'),
    f('effectiveOn', '决定中的生效日期', 'date'),
    f('eventFileIds', '终止或合并决定原件', 'file'),
    f('nonprofitLegalBasis', '适用非营利组织法规和处置依据', 'textarea'),
    f('legalBasisFileIds', '法规、章程和有权机关决议依据', 'file'),
    f('liquidationOrMergerPlanFileIds', '清算或合并的实际藏品处置方案', 'file'),
    f('inventoryFileIds', '本次清点清册和本件对应原件', 'file'),
    {
      ...f('ownership', '本件实际权属', 'select'),
      options: [
        { value: 'STATE', label: '国家所有' },
        { value: 'NON_STATE', label: '非国家所有' },
      ],
    },
    { ...f('movable', '本件是否可移动', 'select'), options: yesNo },
    f('propertyBasisFileIds', '逐件权属和性质依据', 'file'),
    f('publicBenefitPurpose', '承接后的公益用途', 'textarea'),
    { ...f('noPrivateDistribution', '已核实不向出资人等分配剩余财产', 'select'), options: yesNo },
    { ...f('thirdPartyRightsResolved', '第三方权利事项已依法或依约处理', 'select'), options: yesNo },
    f('thirdPartyRightsConclusion', '捐赠、寄存及其他第三方权利处理结论', 'textarea'),
    f('thirdPartyRightsFileIds', '第三方约定及实际权利处理依据', 'file'),
    f('authorizedRepresentativeUserId', '清算或合并依法授权人员本人', 'user'),
    f('representativeAuthorizationFileIds', '授权范围及实际授权原件', 'file'),
    {
      ...f('designation.level', '国家所有可移动文物：指定部门层级', 'select', false),
      options: [{ value: 'PROVINCE', label: '省级文物行政部门' }],
    },
    f('designation.authorityCode', '指定部门稳定标识', 'text', false),
    f('designation.authorityName', '指定部门正式名称', 'text', false),
    f('designation.documentNo', '省级指定决定文号', 'text', false),
    f('designation.issuedOn', '省级指定决定日期', 'date', false),
    f('designation.sourceMuseumName', '决定中的原博物馆名称', 'text', false),
    f('designation.targetMuseumName', '决定中的接收国有博物馆名称', 'text', false),
    f('designation.collectionId', '指定清单对应的本件系统编号', 'text', false),
    f('designation.totalRegNo', '指定清单对应的原总登记号', 'text', false),
    f('designation.fileIds', '省级指定决定及本件清单原件', 'file', false),
  ];
}
