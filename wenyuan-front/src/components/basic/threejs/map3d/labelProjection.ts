import { Vector3 } from 'three';

import type { Map3DCoordinate } from './types';

const MAP_SURFACE_ROTATION_AXIS = new Vector3(1, 0, 0);
const MAP_SURFACE_ROTATION_ANGLE = -Math.PI / 2;
const MAP_HOVER_RAISE_DIRECTION = -1;

export function resolveMap3DLabelLiftZ(options: {
  isHovered: boolean;
  depth: number;
  hoverRaiseScale: number;
}) {
  if (!options.isHovered) {
    return 0;
  }

  return options.depth * 0.45 * options.hoverRaiseScale * MAP_HOVER_RAISE_DIRECTION;
}

export function resolveMap3DAnchorLiftZ(options: {
  baseLiftZ: number;
  isHovered: boolean;
  depth: number;
  hoverRaiseScale: number;
}) {
  return options.baseLiftZ + resolveMap3DLabelLiftZ({
    isHovered: options.isHovered,
    depth: options.depth,
    hoverRaiseScale: options.hoverRaiseScale,
  });
}

export function isProjectableMap3DCamera(camera: any) {
  return !!camera?.projectionMatrix?.elements && !!camera?.matrixWorldInverse?.elements;
}

export function projectMap3DLabelCoordinate(options: {
  labelPosition: Map3DCoordinate;
  center: Map3DCoordinate;
  sceneScale: number;
  liftZ: number;
  camera: any;
}) {
  if (!isProjectableMap3DCamera(options.camera)) {
    return null;
  }

  const worldPoint = new Vector3(
    options.labelPosition[0] - options.center[0],
    options.labelPosition[1] - options.center[1],
    options.liftZ,
  );

  worldPoint.multiplyScalar(options.sceneScale);
  worldPoint.applyAxisAngle(MAP_SURFACE_ROTATION_AXIS, MAP_SURFACE_ROTATION_ANGLE);
  worldPoint.project(options.camera);

  if (
    !Number.isFinite(worldPoint.x)
    || !Number.isFinite(worldPoint.y)
    || !Number.isFinite(worldPoint.z)
  ) {
    return null;
  }

  return {
    left: ((worldPoint.x + 1) / 2) * 100,
    top: ((1 - worldPoint.y) / 2) * 100,
  };
}

export function projectMap3DCoordinate(options: {
  coordinate: Map3DCoordinate;
  center: Map3DCoordinate;
  sceneScale: number;
  liftZ: number;
  camera: any;
}) {
  return projectMap3DLabelCoordinate({
    labelPosition: options.coordinate,
    center: options.center,
    sceneScale: options.sceneScale,
    liftZ: options.liftZ,
    camera: options.camera,
  });
}
