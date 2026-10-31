import type { AlarmRuleConditionVo, AlarmRuleType } from '../typings';
const operators: Record<string, string> = {
  GT: '大于',
  GTE: '大于等于',
  LT: '小于',
  LTE: '小于等于',
  EQ: '等于',
  NE: '不等于',
  BETWEEN: '在区间内',
  OUTSIDE: '在区间外',
};
export function alarmConditionText(condition: AlarmRuleConditionVo, ruleType?: AlarmRuleType) {
  const label =
    ruleType === 'DEVICE_OFFLINE'
      ? '无数据时长（秒）'
      : [condition.recordKey, condition.fieldCode || 'VALUE'].filter(Boolean).join(' / ');
  const op = operators[condition.operator || ''] || condition.operator || '';
  const value = condition.expectedValue ?? condition.thresholdValue ?? '--';
  const target = ['BETWEEN', 'OUTSIDE'].includes(condition.operator || '')
    ? `${condition.thresholdValue ?? '--'} ～ ${condition.thresholdValue2 ?? '--'}`
    : value;
  return `${label} ${op} ${target}`;
}
