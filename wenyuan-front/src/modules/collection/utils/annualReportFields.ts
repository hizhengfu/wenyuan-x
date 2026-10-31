import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export const annualSignedFields = [
  f('statisticianUserId', '实际制表人', 'user'),
  f('keeperUserId', '实际保管负责人', 'user'),
  f('leaderUserId', '实际主要负责人', 'user'),
  f('signedAt', '原报表实际签署时间', 'datetime'),
  f('signedReportFileIds', '完整签署报表原件', 'file'),
  f('reconciliationNote', '原报表与系统历史差异或缺口的核对说明', 'textarea', false),
  f('reconciliationFileIds', '纸质总账、逐件明细和核对原件', 'file', false),
  f('replacementReason', '补报或订正原因', 'textarea', false),
  f('replacementFileIds', '本次补报或订正签署原件', 'file', false),
];
export const annualSubmissionFields = [
  f('authorityName', '主管文物行政部门'),
  f('reportNo', '实际报送编号'),
  f('submittedAt', '实际报送时间', 'datetime'),
  f('submissionFileIds', '实际报送凭据', 'file'),
];
export const annualFilingFields = [
  f('authorityName', '实际备案的主管文物行政部门'),
  f('filingNo', '备案编号'),
  f('filedAt', '实际备案时间', 'datetime'),
  f('receiptFileIds', '实际备案回执', 'file'),
];
export const annualCountFields = [
  { key: 'openingTotal', label: '期初条目' },
  { key: 'newTotal', label: '新增登记条目' },
  { key: 'transferInTotal', label: '调入条目' },
  { key: 'transferOutTotal', label: '调出条目' },
  { key: 'deregTotal', label: '退出条目' },
  { key: 'closingTotal', label: '期末条目' },
];
export const annualQuantityFields = [
  { key: 'opening', label: '期初数量' },
  { key: 'added', label: '新增数量' },
  { key: 'transferIn', label: '调入数量' },
  { key: 'transferOut', label: '调出数量' },
  { key: 'deregistered', label: '退出数量' },
  { key: 'adjustment', label: '信息订正增减（可负）' },
  { key: 'closing', label: '期末数量' },
];
export const annualEvidenceStatus = (row: any) =>
  row.filing ? '已留存实际备案回执' : row.submission ? '已实际报送，待备案回执' : '已签署留档，尚无实际报送凭据';
