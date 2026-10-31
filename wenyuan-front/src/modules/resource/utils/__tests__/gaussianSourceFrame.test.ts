import { describe, expect, it } from 'vitest';
import { gaussianCameraPosition, gaussianFrameFromBounds, gaussianFrameFromPoints } from '../gaussianSourceFrame';

describe('Gaussian PLY source frame', () => {
  it('uses actual decoded coordinates to center an axis-aligned ground plane', () => {
    const points = Array.from({ length: 21 * 21 }, (_, index) => [
      100 + index % 21,
      200 + Math.floor(index / 21),
      4 + (index % 3) * 0.1,
    ]);
    const frame = gaussianFrameFromPoints(points)!;
    expect(frame.center[0]).toBeCloseTo(110, 0);
    expect(frame.center[1]).toBeCloseTo(210, 0);
    expect(frame.up).toEqual([0, 0, 1]);
    expect(frame.inferredUp).toBe(true);
  });

  it('finds a different up axis without using a saved camera position', () => {
    const points = Array.from({ length: 21 * 21 }, (_, index) => [
      100 + index % 21,
      4 + (index % 3) * 0.1,
      200 + Math.floor(index / 21),
    ]);
    expect(gaussianFrameFromPoints(points)?.up).toEqual([0, -1, 0]);
  });

  it('finds a tilted planar normal, while leaving an isotropic cloud unclassified', () => {
    const tilted = Array.from({ length: 21 * 21 }, (_, index) => {
      const x = index % 21 - 10;
      const y = Math.floor(index / 21) - 10;
      return [x, y, x * 0.5 + (index % 3) * 0.01];
    });
    const up = gaussianFrameFromPoints(tilted)!.up;
    expect(Math.abs(up[0])).toBeGreaterThan(0.3);
    expect(Math.abs(up[2])).toBeGreaterThan(0.8);
    const cube = Array.from({ length: 10 * 10 * 10 }, (_, index) => [
      index % 10, Math.floor(index / 10) % 10, Math.floor(index / 100),
    ]);
    expect(gaussianFrameFromPoints(cube)?.inferredUp).toBe(false);
    expect(gaussianFrameFromPoints(cube)?.up).toEqual([0, -1, 0]);
  });

  it('does not let a few flyaway points move the initial focus', () => {
    const points = Array.from({ length: 21 * 21 }, (_, index) => [index % 21, Math.floor(index / 21), 0]);
    points.push([1_000_000, 1_000_000, 1_000_000]);
    const frame = gaussianFrameFromPoints(points)!;
    expect(frame.center[0]).toBeLessThan(20);
    expect(frame.center[1]).toBeLessThan(20);
  });

  it('keeps the default viewing direction away from the chosen up axis', () => {
    const frame = gaussianFrameFromBounds([0, 0, 0], [100, 10, 100])!;
    expect(frame.up).toEqual([0, -1, 0]);
    const position = gaussianCameraPosition(frame.center, 10, frame.up);
    expect(position[1]).toBeLessThan(frame.center[1]);
    expect(Math.abs(position[2] - frame.center[2])).toBeGreaterThan(1);
  });

  it('uses the raw PLY Gaussian convention when bounds have no clear vertical axis', () => {
    const frame = gaussianFrameFromBounds(
      [-5.065, -1.578, -3.309], [3.589, 1.940, 3.694],
    )!;
    expect(frame.inferredUp).toBe(false);
    expect(frame.up).toEqual([0, -1, 0]);
    expect(gaussianCameraPosition([0, 0, 0], 8, frame.up)).toEqual([6, -8, 4.4]);
  });
});
