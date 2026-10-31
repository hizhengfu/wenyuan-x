import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export const gradingLevels = [
  { label: '一级文物', value: 'FIRST' },
  { label: '二级文物', value: 'SECOND' },
  { label: '三级文物', value: 'THIRD' },
  { label: '一般文物', value: 'GENERAL' },
];
export const gradingApplicationFields: EvidenceField[] = [
  f('preliminaryAt', '实际初步鉴选时间', 'datetime'),
  f('preliminaryFileIds', '初步鉴选原件', 'file'),
  { ...f('proposedLevel', '初步建议级别', 'select'), options: gradingLevels },
  f('itemDescription', '本件身份、现状及鉴选说明', 'textarea'),
  f('identificationPhotoFileIds', '本件识别照片', 'file'),
  f('provincialAuthority', '受理的省级文物行政部门'),
  f('applicationNo', '定级申请编号'),
  f('submittedAt', '实际提交时间', 'datetime'),
  f('applicationFileIds', '已提交申请原件', 'file'),
  f('receiptFileIds', '省级实际收件凭据', 'file'),
  f('replacementReason', '再次申请或补正原因', 'textarea', false),
];
export const gradingResultFields: EvidenceField[] = [
  f('provincialAuthority', '组织定级的省级文物行政部门'),
  f('documentNo', '正式定级文书编号'),
  f('determinedAt', '正式定级时间', 'datetime'),
  { ...f('level', '行政正式级别', 'select'), options: gradingLevels },
  f('resultFileIds', '省级正式定级原件', 'file'),
  f('provincialFilingNo', '省级备案编号'),
  f('provincialFiledAt', '省级实际备案时间', 'datetime'),
  f('provincialFilingFileIds', '省级备案原件', 'file'),
];
export const gradingNationalFields: EvidenceField[] = [
  f('submittingProvincialAuthority', '实际报送的省级文物行政部门'),
  f('nationalAuthority', '国务院文物行政部门'),
  f('submittedAt', '省级报国家实际时间', 'datetime'),
  f('submissionFileIds', '省级报送原件', 'file'),
  f('filingNo', '国家备案编号'),
  f('filedAt', '国家实际备案时间', 'datetime'),
  f('receiptFileIds', '国家备案回执原件', 'file'),
];
export function gradingStatus(record: any) {
  if (!record?.result) return '已提交申请，待正式定级及省级备案';
  if (record.result.level === 'FIRST' && !record.nationalFiling) return '一级文物待省级报国家备案';
  return record.ready ? '行政材料已齐，正式采用仍须核对最新定级并完成审批' : '行政材料待核实';
}
