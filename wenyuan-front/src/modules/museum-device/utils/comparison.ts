import type {
  MuseumComparisonBucketValueVo,
  MuseumComparisonSeriesVo,
  MuseumComparisonVo,
  StandardMetricAggregation,
} from '../typings';

export interface MuseumComparisonTableRow {
  occurredAt: number;
  values: Record<string, MuseumComparisonBucketValueVo | undefined>;
}

export interface MuseumComparisonUnitGroup {
  unit: string;
  series: MuseumComparisonSeriesVo[];
}

export function comparisonSeriesName(series: MuseumComparisonSeriesVo): string {
  return `${series.deviceName || series.deviceCode || series.deviceId} · ${series.name || series.key}${series.fieldName ? ` · ${series.fieldName}` : ''}`;
}

export function comparisonUnitGroups(series: MuseumComparisonSeriesVo[]): MuseumComparisonUnitGroup[] {
  const groups = new Map<string, MuseumComparisonSeriesVo[]>();
  series.forEach((item) => {
    const unit = item.unit?.trim() || '无单位';
    const current = groups.get(unit) || [];
    current.push(item);
    groups.set(unit, current);
  });
  return [...groups.entries()].map(([unit, items]) => ({ unit, series: items }));
}

export function comparisonTableRows(data: MuseumComparisonVo): MuseumComparisonTableRow[] {
  return (data.buckets || []).map((bucket) => ({
    occurredAt: bucket.occurredAt,
    values: Object.fromEntries((bucket.values || []).map((value) => [value.seriesId, value])),
  }));
}

export function aggregationLabel(aggregation: StandardMetricAggregation): string {
  if (aggregation === 'INCREMENT') return '合计值';
  if (aggregation === 'CUMULATIVE') return '桶末值';
  if (aggregation === 'STATE') return '状态末值';
  return '平均值';
}

export function comparisonSummaryValue(series: MuseumComparisonSeriesVo): number | null | undefined {
  if (series.aggregation === 'INCREMENT') return series.sumValue;
  if (series.aggregation === 'CUMULATIVE' || series.aggregation === 'STATE') return series.latestValue;
  return series.averageValue;
}
