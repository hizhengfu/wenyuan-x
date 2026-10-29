import { describe, expect, it } from 'vitest';
import { canSelectSogLevel, streamedSogBudget, streamedSogLodRange } from '../sogViewerQuality';

describe('Streamed SOG viewer quality', () => {
  it('uses the high-quality auto budget and never clips the complete coarse layer', () => {
    expect(streamedSogBudget(true)).toBe(1_000_000);
    expect(streamedSogBudget(false)).toBe(4_000_000);
    expect(streamedSogBudget(false, 4_500_000)).toBe(4_500_000);
  });

  it('keeps coarse fallback available while refining toward the selected LOD', () => {
    expect(streamedSogLodRange(-1, 4)).toEqual({ min: 0, max: 3 });
    expect(streamedSogLodRange(1, 4)).toEqual({ min: 1, max: 3 });
    expect(streamedSogLodRange(3, 4)).toEqual({ min: 3, max: 3 });
    expect(() => streamedSogLodRange(4, 4)).toThrow('精度级别无效');
  });

  it('does not offer oversized complete levels on mobile', () => {
    expect(canSelectSogLevel(true, 2_000_000)).toBe(true);
    expect(canSelectSogLevel(true, 3_032_500)).toBe(false);
    expect(canSelectSogLevel(false, 34_000_000)).toBe(true);
  });
});
