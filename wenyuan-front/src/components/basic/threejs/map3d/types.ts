export type Map3DCoordinate = [number, number];

export type Map3DSupportedGeometry = 'Polygon' | 'MultiPolygon';

export interface Map3DFeatureProperties {
  [key: string]: unknown;
  name?: string;
  center?: Map3DCoordinate;
  centroid?: Map3DCoordinate;
}

export interface Map3DPolygonGeometry {
  type: 'Polygon';
  coordinates: number[][][];
}

export interface Map3DMultiPolygonGeometry {
  type: 'MultiPolygon';
  coordinates: number[][][][];
}

export type Map3DGeometry = Map3DPolygonGeometry | Map3DMultiPolygonGeometry;

export interface Map3DFeature {
  type: 'Feature';
  properties: Map3DFeatureProperties;
  geometry: Map3DGeometry;
}

export interface Map3DFeatureCollection {
  type: 'FeatureCollection';
  features: Map3DFeature[];
}

export interface Map3DBounds {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

export interface Map3DProjectedRegion {
  key: string;
  index: number;
  name: string;
  feature: Map3DFeature;
  properties: Map3DFeatureProperties;
  polygons: Map3DCoordinate[][][];
  labelPosition: Map3DCoordinate;
  geometryCenter: Map3DCoordinate;
}

export interface Map3DProjectionResult {
  regions: Map3DProjectedRegion[];
  outlineRings: Map3DCoordinate[][];
  bounds: Map3DBounds | null;
  geoBounds: Map3DBounds | null;
  center: Map3DCoordinate;
  warnings: string[];
}

export interface Map3DTooltipField {
  key: string;
  label?: string;
  formatter?: (value: unknown, properties: Map3DFeatureProperties) => string;
}

export interface Map3DTooltipRow {
  key: string;
  label: string;
  value: string;
}

export interface Map3DPointerPayload {
  region: Map3DProjectedRegion;
  clientX: number;
  clientY: number;
}

export interface Map3DRegionEvent {
  eventType: 'enter' | 'leave' | 'click';
  feature: Map3DFeature;
  properties: Map3DFeatureProperties;
  index: number;
  name: string;
}

export interface Map3DTopTextureEffect {
  enabled?: boolean;
  mode?: 'procedural' | 'tianditu' | 'image';
  opacity?: number;
  repeat?: number;
  imageUrl?: string;
  token?: string;
  tileUrl?: string;
  zoom?: number;
  maxCanvasSize?: number;
  subdomains?: string[];
  gradientColors?: [string, string, string];
  patchColor?: string;
  lineColor?: string;
  shadowColor?: string;
  brightness?: number;
  contrast?: number;
  saturation?: number;
  tintColor?: string;
  tintOpacity?: number;
  screenColor?: string;
  screenOpacity?: number;
}

export interface Map3DSideScanEffect {
  enabled?: boolean;
  color?: string;
  scanColor?: string;
  opacity?: number;
  speed?: number;
  width?: number;
  direction?: 'bottom-to-top' | 'top-to-bottom';
  strength?: number;
}

export interface Map3DEdgeFlowEffect {
  enabled?: boolean;
  scope?: 'region' | 'outline' | 'both';
  color?: string;
  pointSize?: number;
  speed?: number;
  length?: number;
  lineOpacity?: number;
  minRingLengthRatio?: number;
}

export interface Map3DBoundaryLineEffect {
  enabled?: boolean;
  color?: string;
  width?: number;
  opacity?: number;
}

export interface Map3DBeaconEffect {
  enabled?: boolean;
  includeNames?: string[];
  positionOffsets?: Record<string, {
    x?: number;
    y?: number;
  }>;
  imageUrl?: string;
  imageWidth?: number;
  imageHeight?: number;
  imageOpacity?: number;
  screenWidth?: number;
  screenHeight?: number;
  labelOffset?: number;
  height?: number;
  labelLift?: number;
  labelColor?: string;
  showLabels?: boolean;
}

export interface Map3DVisualEffects {
  topTexture?: Map3DTopTextureEffect;
  sideScan?: Map3DSideScanEffect;
  edgeFlow?: Map3DEdgeFlowEffect;
  boundaryLine?: Map3DBoundaryLineEffect;
  beacon?: Map3DBeaconEffect;
}

export interface ProjectMapRegionsOptions {
  nameKey: string;
}

export interface BuildTooltipRowsOptions {
  nameKey: string;
  fields?: Map3DTooltipField[];
}
