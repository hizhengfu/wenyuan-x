/** 单位资质以证书和核定范围为准；旧等级仅显示历史记录。 */
export const unitQualificationOptions = [{ label: '不分等级（单位资质）', value: 'UNGRADED' }];
export const personalQualificationOptions = [
  { label: '初级（个人）', value: 'PRIMARY_LEVEL' },
  { label: '中级（个人）', value: 'INTERMEDIATE_LEVEL' },
  { label: '高级（个人）', value: 'SENIOR_LEVEL' },
];
export function qualificationLevelLabel(value?: string | null): string {
  const labels: Record<string, string> = { FIRST: '一级（历史记录）', SECOND: '二级（历史记录）', THIRD: '三级（历史记录）' };
  return [...unitQualificationOptions, ...personalQualificationOptions].find(item => item.value === value)?.label
    || labels[value || ''] || value || '未登记';
}
export function qualificationLevelOptions(holderType?: string, historicalValue?: string) {
  const options = holderType === 'ORG' ? unitQualificationOptions : holderType === 'PERSON' ? personalQualificationOptions : [];
  return historicalValue && !options.some(item => item.value === historicalValue)
    ? [...options, { label: `${qualificationLevelLabel(historicalValue)} · 保留原记录`, value: historicalValue }]
    : [...options];
}
