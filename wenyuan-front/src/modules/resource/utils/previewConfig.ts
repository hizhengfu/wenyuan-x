export type ResourcePreviewConfig = {
  cameraPosition?: number[] | null;
  cameraTarget?: number[] | null;
  cameraUp?: number[] | null;
  cameraFov?: number | null;
  backgroundColor?: string | null;
};

export type CameraView = {
  position: [number, number, number];
  target: [number, number, number];
  up: [number, number, number];
  fov?: number;
};

export function validVector(value?: number[] | null): value is [number, number, number] {
  return Array.isArray(value) && value.length === 3 && value.every(Number.isFinite);
}

export function configuredCameraView(config?: ResourcePreviewConfig | null): CameraView | null {
  if (!validVector(config?.cameraPosition) || !validVector(config?.cameraTarget)) return null;
  const direction = config.cameraPosition.map((value, index) => value - config.cameraTarget![index]);
  if (Math.hypot(...direction) <= 1e-9) return null;
  const up = validVector(config.cameraUp) && Math.hypot(...config.cameraUp) > 1e-9 ? [...config.cameraUp] : [0, 1, 0];
  return {
    position: [...config.cameraPosition],
    target: [...config.cameraTarget],
    up: up as [number, number, number],
    fov: Number.isFinite(config.cameraFov) ? Number(config.cameraFov) : undefined,
  };
}

export function previewBackground(config: ResourcePreviewConfig | null | undefined, fallback: string): string {
  return /^#[0-9a-f]{6}$/i.test(config?.backgroundColor || '') ? config!.backgroundColor! : fallback;
}
