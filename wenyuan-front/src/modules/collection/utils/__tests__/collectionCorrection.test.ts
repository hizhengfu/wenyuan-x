import { describe, expect, it } from 'vitest';
import {
  correctionFieldOptions,
  initialCorrectionValue,
  normalizeCorrectionValue,
} from '../collectionCorrection';

describe('collection correction acquisition fields', () => {
  it('offers exact date, date range, and year as independent correction fields', () => {
    expect(correctionFieldOptions.map((item) => item.value)).toEqual(
      expect.arrayContaining(['acquireDate', 'acquireDateRange', 'acquireYear']),
    );
  });

  it('preserves legacy survey date ranges and years as text', () => {
    const row = { acquireDateRange: '1949.10.1-1965', acquireYear: '1965' };
    expect(initialCorrectionValue(row, 'acquireDateRange')).toBe('1949.10.1-1965');
    expect(initialCorrectionValue(row, 'acquireYear')).toBe('1965');
    expect(normalizeCorrectionValue('acquireDateRange', ' 1949.10.1-1965 ')).toBe('1949.10.1-1965');
    expect(normalizeCorrectionValue('acquireYear', ' 1965 ')).toBe('1965');
  });
});
