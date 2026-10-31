import { describe, expect, it } from 'vitest';
import { inferPlanarSceneUp, resolveOrbitUp, sourceSceneUp } from '../cameraOrientation';

describe('camera orientation', () => {
  it('infers the thin axis only for clearly planar bounds', () => {
    expect(inferPlanarSceneUp([-80, -100, -2], [60, 40, 22])).toEqual([0, 0, 1]);
    expect(inferPlanarSceneUp([-2, -2, -2], [2, 2, 2])).toBeNull();
  });

  it('replaces an up vector nearly parallel to the saved viewing direction', () => {
    expect(resolveOrbitUp(
      [0, 30, 5], [0, 0, 5], [0, 1, 0],
      [-80, -100, -2], [60, 40, 22],
    )).toEqual([0, 0, 1]);
  });

  it('keeps a usable explicit up vector even when bounds suggest another axis', () => {
    expect(resolveOrbitUp(
      [10, 10, 10], [0, 0, 0], [0, 5, 0],
      [-80, -100, -2], [60, 40, 22],
    )).toEqual([0, 1, 0]);
  });

  it('infers the SOG orbit axis from source bounds, not the saved camera pose', () => {
    expect(sourceSceneUp(
      [-81.395195, -99.34996, -2.5225005],
      [62.51062, 38.736347, 21.712835],
    )).toEqual([0, 0, 1]);
    expect(sourceSceneUp([-2, -2, -2], [2, 2, 2])).toEqual([0, 1, 0]);
  });

  it('uses a stable orthogonal axis when bounds are unavailable or inconclusive', () => {
    expect(resolveOrbitUp([0, 10, 0], [0, 0, 0], [0, 1, 0])).toEqual([0, 0, 1]);
    expect(resolveOrbitUp([10, 0, 0], [0, 0, 0], undefined)).toEqual([0, 1, 0]);
  });

  it('does not use the thin axis when it is also parallel to the viewing direction', () => {
    expect(resolveOrbitUp(
      [0, 10, 0], [0, 0, 0], [0, 1, 0],
      [-10, -0.1, -10], [10, 0.1, 10],
    )).toEqual([0, 0, 1]);
  });
});
