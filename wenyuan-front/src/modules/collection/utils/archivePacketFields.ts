import type { EvidenceField } from './evidenceFields';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
export const archiveSections = [
  ['BASIC', '基本信息'],
  ['ACQUISITION', '取得'],
  ['IDENTIFICATION', '鉴定'],
  ['GRADING', '定级'],
  ['STORAGE', '保管'],
  ['CONSERVATION', '保护'],
  ['RESEARCH', '研究'],
  ['EXHIBITION', '展示'],
  ['USE', '利用'],
  ['OTHER_HISTORY', '其他历史事项'],
] as const;
export const archiveSectionStatuses = [
  { value: 'INCLUDED', label: '已归入实际原件' },
  { value: 'NOT_OCCURRED', label: '截至编制日尚未发生' },
  { value: 'MISSING', label: '有事项但原件待补齐' },
];
export const jurisdictionFields = (revision = false): EvidenceField[] => [
  {
    ...f('kind', '本馆文物行政主管归属', 'select'),
    options: [
      { value: 'LOCAL_COMPETENT', label: '地方文物行政部门主管' },
      { value: 'NATIONAL_COMPETENT', label: '国务院文物行政部门主管' },
    ],
  },
  f('competentAuthorityName', '本馆主管文物行政部门'),
  f('localArchiveAuthorityName', '属地文物档案实际接收部门'),
  f('nationalAuthorityName', '国务院文物行政部门'),
  f('effectiveOn', '实际归属依据生效日期', 'date'),
  f('verifiedAt', '实际核实日期时间', 'datetime'),
  f('responsibleUserId', '核实的本馆主要负责人', 'user'),
  f('basisFileIds', '主管归属及档案受理程序依据原件', 'file'),
  f('replacementReason', '本次归属或名称订正原因', 'textarea', revision),
  f('replacementFileIds', '本次核实或订正依据', 'file', revision),
];
export const archivePacketFields = (revision = false): EvidenceField[] => [
  f('archiveNo', '本版实际档案编号'),
  {
    ...f('format', '实际档案保存形式', 'select'),
    options: [
      { value: 'PAPER', label: '纸质档案' },
      { value: 'ELECTRONIC', label: '电子档案' },
      { value: 'BOTH', label: '纸质和电子档案' },
    ],
  },
  f('preparedAt', '本版档案实际编制核验时间', 'datetime'),
  f('archivistUserId', '本版实际编制人员', 'user'),
  f('physicalLocation', '纸质档案实际保存位置', 'text', false),
  f('electronicLocation', '电子档案实际卷宗或存储位置', 'text', false),
  f('inventoryFileIds', '实际档案目录原件', 'file'),
  f('photographFileIds', '识别照片原件', 'file'),
  f('signedVerificationFileIds', '编制核验签署原件', 'file'),
  f('replacementReason', '补充或订正档案的实际原因', 'textarea', revision),
  f('replacementFileIds', '本次补充或订正依据原件', 'file', revision),
];
export const archiveSubmissionFields: EvidenceField[] = [
  {
    ...f('target', '本次实际报送对象', 'select'),
    options: [
      { value: 'COMPETENT', label: '属地文物档案接收部门' },
      { value: 'NATIONAL', label: '国务院文物行政部门' },
    ],
  },
  f('authorityName', '实际报送文物行政机关'),
  f('submittedAt', '实际报送时间', 'datetime'),
  f('submissionNo', '实际报送编号'),
  f('submissionFileIds', '实际报送凭据原件', 'file'),
];
export const archiveReceiptFields = [
  f('authorityName', '对应报送文物行政机关'),
  f('receivedAt', '实际收到备案回执时间', 'datetime'),
  f('filingNo', '实际档案备案编号'),
  f('receiptFileIds', '实际档案备案回执原件', 'file'),
];
export const archiveSectionLabel = (key: string) =>
  archiveSections.find(([value]) => value === key)?.[1] ||
  ({ NATURE_UNVERIFIED: '藏品性质待核实', GRADE_UNVERIFIED: '正式级别待核实' } as Record<string, string>)[key] ||
  key;
export const archiveTargetLabel = (key: string) => (key === 'NATIONAL' ? '国务院文物行政部门' : '属地文物档案接收部门');
export function archivePacketStatus(row: any) {
  if (row.coverageIssues?.length) return '目录或原件有缺项，尚不能认定建档完成';
  if (!row.matchesCurrentCatalog) return '该版档案保留，当前正式编目信息待更新';
  if (row.requiredTargets?.length === 0) return '已编制档案，其他藏品不适用文物档案备案';
  if (row.requiredTargets == null) return '已编制档案，备案归属待核实';
  if (!row.filingCompleteForPacket) return '已编制档案，实际报送或备案回执未齐';
  return row.matchesCurrentJurisdiction ? '已留存本版所需实际备案回执' : '旧备案回执保留，当前主管归属待核对';
}
