/** 标准指标显示标准名称；扩展指标优先使用厂商原名，编码只作为最后回退。 */
export function metricDisplayName(
  key: string,
  standardName?: string | null,
  sourceMetricName?: string | null,
): string {
  const code = key?.trim() || '';
  const standard = standardName?.trim();
  const source = sourceMetricName?.trim();
  return (code.toUpperCase().startsWith('EXT_') ? source || standard : standard) || code || '--';
}
