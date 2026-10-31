import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export const paperBookFields = [
  f('bookNo', '实际纸质账册号'),
  f('title', '账册名称'),
  f('openedOn', '实际建账日期', 'date'),
  f('pageCount', '账册总页数', 'number'),
  f('coverageDescription', '原账册涵盖范围', 'textarea'),
  f('contentsFileIds', '原始账册目录及封面原件', 'file'),
];
export const paperCustodyFields = (update = false, transfer = false): EvidenceField[] => [
  f('checkedAt', '实际保管核查时间', 'datetime'),
  f('keeperUserId', '实际总登记账专管人员', 'user'),
  f('storageLocation', '纸账实际保管位置'),
  {
    ...f('condition', '保存状况', 'select'),
    options: [
      { value: 'GOOD', label: '原件记载保存正常' },
      { value: 'ISSUE', label: '存在保存异常' },
    ],
  },
  f('conditionDescription', '实际保存状况说明', 'textarea'),
  f('signedCustodyFileIds', '实际签署保管核查原件', 'file'),
  f('fromKeeperUserId', '前任纸账专管人员', 'user', transfer),
  f('handoverFileIds', '双方实际签署移交原件', 'file', transfer),
  f('reason', '本次核查、搬移、移交或订正事由', 'textarea', update),
  f('handlingDescription', '异常及恢复处理说明', 'textarea', false),
  f('handlingFileIds', '实际处理原件', 'file', false),
  f('nextCheckOn', '保存异常的后续复核日期', 'date', false),
];
export function paperBookPayload(value: Record<string, any>) {
  const result: any = {},
    custody: any = {};
  for (const f of paperBookFields) result[f.key] = value[f.key];
  for (const f of paperCustodyFields()) if (value[f.key] !== undefined) custody[f.key] = value[f.key];
  return { ...result, custody };
}
export const reconciliationFields = (review = false): EvidenceField[] => [
  ...(!review
    ? [f('referenceNo', '本次实际核对编号'), f('periodFrom', '核对期间起', 'date'), f('periodTo', '核对期间止', 'date')]
    : []),
  f('checkedAt', '双方实际核对签署时间', 'datetime'),
  f('ledgerKeeperUserId', '实际总账专管人员', 'user'),
  f('financeUserId', '实际财务核对人员', 'user'),
  f('scopeDescription', '本次实际核对范围', 'textarea'),
  f('signedReportFileIds', '双方签署核对报告原件', 'file'),
  f('paperScheduleFileIds', '纸账逐项明细原件', 'file'),
  f('financialScheduleFileIds', '财务账逐项明细原件', 'file'),
  f('nextCheckOn', '馆方确定的下次核对日期', 'date'),
  f('emptyScopeReason', '空范围核对依据', 'textarea', false),
  f('replacementReason', '实际处理复核或订正说明', 'textarea', review),
  f('replacementFileIds', '本次复核或订正原件', 'file', review),
];
export const reconciliationStatus = (record: any) =>
  !record.content.rows?.length
    ? '空范围核对原件'
    : record.differences?.length
      ? '本次明细有差异待处理'
      : '本次明细账账一致';
export const accountFields = [
  { key: 'totalRegNo', label: '总登记号' },
  { key: 'name', label: '原账名称' },
  { key: 'quantity', label: '原账数量' },
  { key: 'unit', label: '计量单位' },
];

export const paperMetadataCorrectionFields = [
  ...paperBookFields,
  {
    key: 'metadataCorrectionFileIds',
    label: '本次账册资料订正依据原件',
    type: 'file',
    required: true,
  } as EvidenceField,
];
export function paperMetadataCorrectionPayload(value: Record<string, any>) {
  return {
    ...paperBookPayload(value).custody,
    correctedMetadata: Object.fromEntries(paperBookFields.map((f) => [f.key, value[f.key]])),
    metadataCorrectionFileIds: value.metadataCorrectionFileIds,
  };
}
