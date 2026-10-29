import { describe, expect, it } from 'vitest';
import { formatMetricNumber, formatMetricValue } from '../metricValue';

describe('museum device metric value formatting', () => {
  it('formats numeric values and units consistently', () => {
    expect(formatMetricValue({ numericValue: 1234.5678, unit: '℃' })).toBe('1,234.568 ℃');
    expect(formatMetricValue({ numericValue: 0, unit: 'V' })).toBe('0 V');
    expect(formatMetricNumber(-12.3456)).toBe('-12.346');
    expect(formatMetricNumber(null)).toBe('--');
  });

  it('uses business-facing boolean labels', () => {
    expect(formatMetricValue({ booleanValue: true })).toBe('是');
    expect(formatMetricValue({ booleanValue: false })).toBe('否');
  });

  it('formats string and empty values', () => {
    expect(formatMetricValue({ stringValue: '正常' })).toBe('正常');
    expect(formatMetricValue({ stringValue: '', unit: '级' })).toBe('--');
    expect(formatMetricValue({})).toBe('--');
    expect(formatMetricValue({ numericValue: Number.NaN })).toBe('--');
  });
});
