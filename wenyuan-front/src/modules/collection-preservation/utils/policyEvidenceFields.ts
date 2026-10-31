import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
export const policyDuties: Record<string, string> = {
  CONSERVATION: '保护修复责任人',
  KEEPER: '保管负责人',
  SECURITY: '安全保卫人员',
  INFORMATION: '信息化人员',
  DEPARTMENT: '其他参与部门人员',
};
export const environmentFactors: Record<string, string> = {
  TEMPERATURE: '温度',
  HUMIDITY: '湿度',
  LIGHT: '光照',
  AIR_POLLUTION: '空气污染物',
  PESTS: '有害生物',
};
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
export const participantFields: EvidenceField[] = [
  { key: 'duty', label: '实际职责', type: 'select', options: options(policyDuties), required: true },
  { key: 'departmentId', label: '实际所属部门', type: 'group', required: true },
  { key: 'userId', label: '实际参与人员', type: 'user', required: true },
  { key: 'responsibilities', label: '本次具体职责', type: 'textarea', required: true },
];
export const assessmentFields: EvidenceField[] = [
  { key: 'assessedAt', label: '实际共同评估时间', type: 'datetime', required: true },
  { key: 'participants', label: '实际参与部门与人员', type: 'records', children: participantFields, required: true },
  { key: 'applicableCollectionsAndLocations', label: '适用藏品与实际位置', type: 'textarea', required: true },
  { key: 'thresholdBasis', label: '实际阈值及单位依据', type: 'textarea', required: true },
  {
    key: 'environmentMeasures',
    label: '各项环境风险及措施',
    type: 'records',
    required: true,
    children: [
      { key: 'factor', label: '环境因素', type: 'select', options: options(environmentFactors), required: true },
      { key: 'risksAndBasis', label: '风险及评估依据', type: 'textarea', required: true },
      { key: 'monitoringAndControl', label: '实际监测与控制安排', type: 'textarea', required: true },
    ],
  },
  {
    key: 'monitoringFreshness',
    label: '各指标采样有效期',
    type: 'records',
    required: true,
    hint: '由实际共同评估确定，不提供统一默认期限。超期或缺少依据的读数不能证明当前环境安全。',
    children: [
      { key: 'metricCode', label: '本版指标编码', required: true },
      { key: 'maximumSampleAgeSeconds', label: '最大采样有效期（秒）', type: 'number', required: true },
      { key: 'basis', label: '实际评估依据', type: 'textarea', required: true },
      { key: 'missingOrLateResponse', label: '缺测或超期处置', type: 'textarea', required: true },
    ],
  },
  { key: 'facilitiesAndMaintenance', label: '设施设备及检查维护', type: 'textarea', required: true },
  { key: 'monitoringAndEvaluation', label: '监测记录与评估安排', type: 'textarea', required: true },
  { key: 'abnormalResponse', label: '异常控制、报告与复查', type: 'textarea', required: true },
  { key: 'fragileProtection', label: '脆弱藏品防护与限制', type: 'textarea', required: true },
  { key: 'coordinationMechanism', label: '常态协作与职责安排', type: 'textarea', required: true },
  { key: 'fileIds', label: '实际共同评估原件', type: 'file', required: true },
  { key: 'replacementReason', label: '修订原因（修订时必填）', type: 'textarea' },
];
export const policySignatureFields: EvidenceField[] = [
  { key: 'opinion', label: '本人实际核对意见', type: 'textarea', required: true },
  { key: 'fileIds', label: '本人签署原件', type: 'file', required: true },
];
export const policyEvidenceStates: Record<string, string> = {
  LEGACY_REVIEW_REQUIRED: '历史运行方案待补共同评估',
  MISSING_ASSESSMENT: '待登记本版共同评估',
  DEFINITION_CHANGED: '阈值、范围或指标含义已变化',
  PENDING_SIGNATURES: '待参与人员本人核对',
  PERSONNEL_REVIEW_REQUIRED: '当前人员任职待核查',
  FRESHNESS_REVIEW_REQUIRED: '须补充实际采样有效期评估',
  READY: '本版共同评估及本人核对已齐全',
};
