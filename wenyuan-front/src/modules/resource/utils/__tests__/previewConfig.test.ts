import { describe, expect, it } from 'vitest';
import { configuredCameraView, previewBackground } from '../previewConfig';

describe('configuredCameraView', () => {
  it('accepts one complete model-coordinate camera view', () => {
    expect(configuredCameraView({
      cameraPosition: [10, 20, 30],
      cameraTarget: [1, 2, 3],
      cameraUp: [0, 0, 1],
      cameraFov: 42,
    })).toEqual({
      position: [10, 20, 30],
      target: [1, 2, 3],
      up: [0, 0, 1],
      fov: 42,
    });
  });

  it('ignores incomplete or degenerate views', () => {
    expect(configuredCameraView({ cameraPosition: [1, 2, 3] })).toBeNull();
    expect(configuredCameraView({ cameraPosition: [1, 2, 3], cameraTarget: [1, 2, 3] })).toBeNull();
  });
});

describe('previewBackground', () => {
  it('accepts only a persisted hex color', () => {
    expect(previewBackground({ backgroundColor: '#102030' }, '#ffffff')).toBe('#102030');
    expect(previewBackground({ backgroundColor: 'red' }, '#ffffff')).toBe('#ffffff');
  });
});
