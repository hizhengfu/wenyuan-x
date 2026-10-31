import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
const yn = [
  { label: '安全', value: 'YES' },
  { label: '存在不安全因素', value: 'NO' },
];
export const aftercareFields: EvidenceField[] = [
  { key: 'checkedAt', label: '实际检查时间', type: 'datetime', required: true },
  { key: 'checkerUserId', label: '实际检查人员', type: 'user', required: true },
  { key: 'actualLocation', label: '实际存放地点', required: true },
  { key: 'conditionSafe', label: '保存状况核查', type: 'select', options: yn, required: true },
  { key: 'environmentSafe', label: '环境安全核查', type: 'select', options: yn, required: true },
  { key: 'conditionAndDisease', label: '保存状况及病害', type: 'textarea', required: true },
  { key: 'environmentAndThreats', label: '实际环境及危险因素', type: 'textarea', required: true },
  { key: 'careAndMeasures', label: '养护工作与处置措施', type: 'textarea', required: true },
  { key: 'conditionFileIds', label: '现状及病害检查原件', type: 'file', required: true },
  { key: 'environmentFileIds', label: '实际环境记录原件', type: 'file', required: true },
  { key: 'signedCheckFileIds', label: '签署检查原件', type: 'file', required: true },
];
export const aftercareReviewFields: EvidenceField[] = [
  { key: 'reviewedAt', label: '实际复核时间', type: 'datetime', required: true },
  { key: 'keeperUserId', label: '保管负责人', type: 'user', required: true },
  { key: 'opinion', label: '实际复核意见', type: 'textarea', required: true },
  { key: 'signedReviewFileIds', label: '签署复核原件', type: 'file', required: true },
];
export function aftercareValues(v: Record<string, any>) {
  const bool = (x: any) => (x === 'YES' ? true : x === 'NO' ? false : x);
  return {
    ...v,
    conditionSafe: bool(v.conditionSafe),
    environmentSafe: bool(v.environmentSafe),
    resolvesCheckIds: v.resolvesCheckIds || [],
  };
}
