import { describe, expect, it } from 'vitest';
import { sogMoveDeltaSeconds, sogSceneFlySpeed } from '../sogCameraMotion';

describe('SOG camera movement', () => {
  it('scales a fixed scene movement speed from the original bounds', () => {
    const boundsMin = [0, 0, 0];
    const boundsMax = [30, 40, 0];
    expect(sogSceneFlySpeed(boundsMin, boundsMax)).toBe(2.5);
    expect(sogSceneFlySpeed([0, 0, 0], [3, 4, 0])).toBe(0.25);
    expect(sogSceneFlySpeed([0, 0, 0], [0.1, 0, 0])).toBe(0.1);
  });

  it('keeps a finite fallback for absent or invalid bounds', () => {
    expect(sogSceneFlySpeed()).toBe(0.64);
    expect(sogSceneFlySpeed([0, 0, 0], [0, 0, 0])).toBe(0.64);
    expect(sogSceneFlySpeed([0, 0, 0], [Number.NaN, 1, 1])).toBe(0.64);
  });

  it('uses elapsed time through low frame rates but limits long pauses', () => {
    expect(sogMoveDeltaSeconds(1016, 1000)).toBeCloseTo(0.016);
    expect(sogMoveDeltaSeconds(1200, 1000)).toBeCloseTo(0.2);
    expect(sogMoveDeltaSeconds(2000, 1000)).toBe(0.25);
    expect(sogMoveDeltaSeconds(1000, 0)).toBe(0);
  });
});
