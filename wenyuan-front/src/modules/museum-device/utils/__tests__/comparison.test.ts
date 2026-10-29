import { describe, expect, it } from 'vitest';
import type { MuseumComparisonSeriesVo, MuseumComparisonVo } from '../../typings';
import {
  aggregationLabel,
  comparisonSeriesName,
  comparisonSummaryValue,
  comparisonTableRows,
  comparisonUnitGroups,
} from '../comparison';

describe('museum device comparison presentation', () => {
  it('groups axes by unit and keeps device identity in the legend', () => {
    const series = [
      metric('A', 'DEVICE-1', '一号设备', 'TEMP', '温度', '℃'),
      metric('B', 'DEVICE-2', '二号设备', 'TEMP', '温度', '℃'),
      metric('C', 'DEVICE-3', '三号设备', 'CO2', 'CO₂', 'ppm'),
    ];

    expect(comparisonUnitGroups(series).map((group) => [group.unit, group.series.length])).toEqual([
      ['℃', 2],
      ['ppm', 1],
    ]);
    expect(comparisonSeriesName(series[1])).toBe('二号设备 · 温度');
  });

  it('pivots aligned buckets without inventing missing values', () => {
    const data = {
      from: 0,
      to: 120,
      bucketMinutes: 60,
      qualities: ['NORMAL'],
      includeBackfill: true,
      series: [metric('A', 'D1', '设备1', 'TEMP', '温度', '℃'), metric('B', 'D2', '设备2', 'TEMP', '温度', '℃')],
      buckets: [{ occurredAt: 0, values: [{ seriesId: 'A', value: 20, minValue: 18, maxValue: 22, count: 3 }] }],
    } satisfies MuseumComparisonVo;

    const rows = comparisonTableRows(data);
    expect(rows[0].values.A?.value).toBe(20);
    expect(rows[0].values.B).toBeUndefined();
  });

  it('uses the metric aggregation for summary labels and values', () => {
    const increment = {
      ...metric('A', 'D1', '设备1', 'ENTRY', '入馆人数', '人次'),
      aggregation: 'INCREMENT' as const,
      sumValue: 12,
    };
    expect(aggregationLabel(increment.aggregation)).toBe('合计值');
    expect(comparisonSummaryValue(increment)).toBe(12);
  });
});

function metric(
  seriesId: string,
  deviceId: string,
  deviceName: string,
  key: string,
  name: string,
  unit: string,
): MuseumComparisonSeriesVo {
  return {
    seriesId,
    deviceId,
    deviceCode: deviceId,
    deviceName,
    key,
    name,
    fieldCode: 'value',
    fieldName: '',
    unit,
    aggregation: 'GAUGE',
    total: 1,
    bucketCount: 1,
    expectedBucketCount: 1,
    coverageRate: 1,
    locationChangedInRange: false,
  };
}
