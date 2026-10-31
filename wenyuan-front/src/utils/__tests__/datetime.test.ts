import { describe, expect, it } from 'vitest';
import { formatLocalDateValue, hasLocalDateValue } from '../datetime';

describe('formatLocalDateValue', () => {
  it.each([
    [new Date(1960, 0, 2).getTime(), '1960-01-02'],
    [new Date(1970, 0, 1).getTime(), '1970-01-01'],
    [new Date(1970, 0, 2).getTime(), '1970-01-02'],
    [new Date(1971, 0, 2).getTime(), '1971-01-02'],
  ])('formats backend LocalDate millisecond value %s without changing the year', (value, expected) => {
    expect(formatLocalDateValue(value)).toBe(expected);
  });

  it('formats ISO local-date text without a timezone shift', () => {
    expect(formatLocalDateValue('1960-01-02')).toBe('1960-01-02');
  });

  it('treats the unix epoch value zero as a selected date', () => {
    expect(hasLocalDateValue(0)).toBe(true);
    expect(hasLocalDateValue(null)).toBe(false);
  });
});
