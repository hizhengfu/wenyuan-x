export { default as Map3D } from './Map3D.vue';

export interface Map3DCameraConfig {
  position: [number, number, number];
  up?: [number, number, number];
  lookAt?: [number, number, number];
  fov: number;
  near: number;
  far: number;
}

export type {
  Map3DCoordinate,
  Map3DFeature,
  Map3DFeatureCollection,
  Map3DFeatureProperties,
  Map3DPointerPayload,
  Map3DProjectedRegion,
  Map3DRegionEvent,
  Map3DTooltipField,
  Map3DTooltipRow,
  Map3DVisualEffects,
} from './types';
