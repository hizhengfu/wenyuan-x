import { geoIdentity } from 'd3-geo';

import type {
  BuildTooltipRowsOptions,
  Map3DBounds,
  Map3DCoordinate,
  Map3DFeature,
  Map3DFeatureCollection,
  Map3DFeatureProperties,
  Map3DProjectedRegion,
  Map3DProjectionResult,
  Map3DRegionEvent,
  ProjectMapRegionsOptions,
} from './types';

const DEFAULT_NAME = '未命名区域';
const EMPTY_CENTER: Map3DCoordinate = [0, 0];
const PROJECTION_EXTENT: [Map3DCoordinate, Map3DCoordinate] = [
  [-100, -80],
  [100, 80],
];
const BOUNDARY_POINT_PRECISION = 1000;

interface BoundarySegment {
  key: string;
  from: Map3DCoordinate;
  to: Map3DCoordinate;
  fromKey: string;
  toKey: string;
  count: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object';
}

function isCoordinate(value: unknown): value is Map3DCoordinate {
  return Array.isArray(value)
    && value.length === 2
    && value.every((item) => typeof item === 'number' && Number.isFinite(item));
}

function isFeature(value: unknown): value is Map3DFeature {
  if (!isRecord(value) || value.type !== 'Feature' || !isRecord(value.properties) || !isRecord(value.geometry)) {
    return false;
  }

  return value.geometry.type === 'Polygon' || value.geometry.type === 'MultiPolygon';
}

function getRegionName(properties: Map3DFeatureProperties, nameKey: string): string {
  const rawName = properties[nameKey];
  return typeof rawName === 'string' && rawName.trim() ? rawName.trim() : DEFAULT_NAME;
}

function getPolygonSets(feature: Map3DFeature) {
  return feature.geometry.type === 'Polygon'
    ? [feature.geometry.coordinates]
    : feature.geometry.coordinates;
}

function isCoordinateArray(value: unknown): value is number[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'number' && Number.isFinite(item));
}

function sanitizeRing(ring: unknown): number[][] | null {
  if (!Array.isArray(ring)) {
    return null;
  }

  const coordinates = ring.filter(isCoordinateArray);
  return coordinates.length >= 4 ? coordinates : null;
}

function sanitizePolygon(polygon: unknown): number[][][] | null {
  if (!Array.isArray(polygon)) {
    return null;
  }

  const rings = polygon
    .map((ring) => sanitizeRing(ring))
    .filter((ring): ring is number[][] => ring !== null);

  return rings.length ? rings : null;
}

function sanitizeFeatureGeometry(feature: Map3DFeature): Map3DFeature | null {
  if (feature.geometry.type === 'Polygon') {
    const polygon = sanitizePolygon(feature.geometry.coordinates);
    if (!polygon) {
      return null;
    }

    return {
      ...feature,
      geometry: {
        type: 'Polygon',
        coordinates: polygon,
      },
    };
  }

  if (!Array.isArray(feature.geometry.coordinates)) {
    return null;
  }

  const polygons = feature.geometry.coordinates
    .map((polygon) => sanitizePolygon(polygon))
    .filter((polygon): polygon is number[][][] => polygon !== null);

  if (!polygons.length) {
    return null;
  }

  return {
    ...feature,
    geometry: {
      type: 'MultiPolygon',
      coordinates: polygons,
    },
  };
}

function projectCoordinate(
  projection: ReturnType<typeof geoIdentity>,
  coordinate: number[],
): Map3DCoordinate | null {
  const projected = projection(coordinate as [number, number]);
  if (!projected) {
    return null;
  }

  return [projected[0], projected[1]];
}

function resolveBounds(points: Map3DCoordinate[]): Map3DBounds | null {
  if (!points.length) {
    return null;
  }

  const xs = points.map(([x]) => x);
  const ys = points.map(([, y]) => y);

  return {
    minX: Math.min(...xs),
    maxX: Math.max(...xs),
    minY: Math.min(...ys),
    maxY: Math.max(...ys),
  };
}

function mergeBounds(current: Map3DBounds | null, next: Map3DBounds | null): Map3DBounds | null {
  if (!next) {
    return current;
  }

  if (!current) {
    return next;
  }

  return {
    minX: Math.min(current.minX, next.minX),
    maxX: Math.max(current.maxX, next.maxX),
    minY: Math.min(current.minY, next.minY),
    maxY: Math.max(current.maxY, next.maxY),
  };
}

function getRingArea(ring: Map3DCoordinate[]) {
  if (ring.length < 3) {
    return 0;
  }

  return ring.reduce((area, point, index) => {
    const next = ring[(index + 1) % ring.length];
    return area + point[0] * next[1] - next[0] * point[1];
  }, 0) / 2;
}

function getRingCentroid(ring: Map3DCoordinate[]): Map3DCoordinate | null {
  const signedArea = getRingArea(ring);
  if (!signedArea) {
    return null;
  }

  const factor = ring.reduce<[number, number]>((result, point, index) => {
    const next = ring[(index + 1) % ring.length];
    const cross = point[0] * next[1] - next[0] * point[1];
    result[0] += (point[0] + next[0]) * cross;
    result[1] += (point[1] + next[1]) * cross;
    return result;
  }, [0, 0]);

  return [
    factor[0] / (6 * signedArea),
    factor[1] / (6 * signedArea),
  ];
}

function isPointInRing(point: Map3DCoordinate, ring: Map3DCoordinate[]) {
  let inside = false;
  const [x, y] = point;

  for (let currentIndex = 0, previousIndex = ring.length - 1; currentIndex < ring.length; previousIndex = currentIndex++) {
    const current = ring[currentIndex];
    const previous = ring[previousIndex];
    const intersects = ((current[1] > y) !== (previous[1] > y))
      && (x < ((previous[0] - current[0]) * (y - current[1])) / (previous[1] - current[1]) + current[0]);

    if (intersects) {
      inside = !inside;
    }
  }

  return inside;
}

function isPointInPolygon(point: Map3DCoordinate, polygon: Map3DCoordinate[][]) {
  const [outerRing, ...holes] = polygon;
  if (!outerRing || !isPointInRing(point, outerRing)) {
    return false;
  }

  return !holes.some((hole) => isPointInRing(point, hole));
}

function isPointInPolygons(point: Map3DCoordinate, polygons: Map3DCoordinate[][][]) {
  return polygons.some((polygon) => isPointInPolygon(point, polygon));
}

function getDistanceToSegment(point: Map3DCoordinate, from: Map3DCoordinate, to: Map3DCoordinate) {
  const dx = to[0] - from[0];
  const dy = to[1] - from[1];

  if (!dx && !dy) {
    return Math.hypot(point[0] - from[0], point[1] - from[1]);
  }

  const ratio = Math.max(0, Math.min(1, ((point[0] - from[0]) * dx + (point[1] - from[1]) * dy) / (dx * dx + dy * dy)));
  const closestX = from[0] + ratio * dx;
  const closestY = from[1] + ratio * dy;

  return Math.hypot(point[0] - closestX, point[1] - closestY);
}

function getDistanceToPolygonEdges(point: Map3DCoordinate, polygon: Map3DCoordinate[][]) {
  return polygon.reduce((minDistance, ring) => {
    const ringDistance = ring.reduce((distance, current, index) => {
      const next = ring[(index + 1) % ring.length];
      return Math.min(distance, getDistanceToSegment(point, current, next));
    }, Number.POSITIVE_INFINITY);

    return Math.min(minDistance, ringDistance);
  }, Number.POSITIVE_INFINITY);
}

function resolveBestInteriorPoint(polygons: Map3DCoordinate[][][], gridSize = 36) {
  const allPoints = polygons.flat(2);
  const bounds = resolveBounds(allPoints);
  if (!bounds) {
    return null;
  }

  let bestPoint: Map3DCoordinate | null = null;
  let bestDistance = -1;

  const testPoint = (point: Map3DCoordinate) => {
    const containingPolygon = polygons.find((polygon) => isPointInPolygon(point, polygon));
    if (!containingPolygon) {
      return;
    }

    const distance = getDistanceToPolygonEdges(point, containingPolygon);
    if (distance > bestDistance) {
      bestDistance = distance;
      bestPoint = point;
    }
  };

  polygons.forEach(([outerRing]) => {
    const centroid = getRingCentroid(outerRing);
    if (centroid) {
      testPoint(centroid);
    }
  });

  testPoint([
    (bounds.minX + bounds.maxX) / 2,
    (bounds.minY + bounds.maxY) / 2,
  ]);

  for (let xIndex = 1; xIndex < gridSize; xIndex += 1) {
    for (let yIndex = 1; yIndex < gridSize; yIndex += 1) {
      testPoint([
        bounds.minX + ((bounds.maxX - bounds.minX) * xIndex) / gridSize,
        bounds.minY + ((bounds.maxY - bounds.minY) * yIndex) / gridSize,
      ]);
    }
  }

  return bestPoint;
}

function resolveInteriorPoint(polygons: Map3DCoordinate[][][]) {
  const allPoints = polygons.flat(2);
  const bounds = resolveBounds(allPoints);
  if (!bounds) {
    return EMPTY_CENTER;
  }

  const centroidSum = polygons.reduce<[number, number, number]>((result, [outerRing]) => {
    const centroid = getRingCentroid(outerRing);
    const area = Math.abs(getRingArea(outerRing));
    if (!centroid || !area) {
      return result;
    }

    result[0] += centroid[0] * area;
    result[1] += centroid[1] * area;
    result[2] += area;
    return result;
  }, [0, 0, 0]);

  const centroid: Map3DCoordinate | null = centroidSum[2]
    ? [centroidSum[0] / centroidSum[2], centroidSum[1] / centroidSum[2]]
    : null;

  if (centroid && isPointInPolygons(centroid, polygons)) {
    return centroid;
  }

  const boundsCenter: Map3DCoordinate = [
    (bounds.minX + bounds.maxX) / 2,
    (bounds.minY + bounds.maxY) / 2,
  ];
  if (isPointInPolygons(boundsCenter, polygons)) {
    return boundsCenter;
  }

  const gridSize = 16;
  let bestPoint: Map3DCoordinate | null = null;
  let bestDistance = -1;

  for (let xIndex = 1; xIndex < gridSize; xIndex += 1) {
    for (let yIndex = 1; yIndex < gridSize; yIndex += 1) {
      const point: Map3DCoordinate = [
        bounds.minX + ((bounds.maxX - bounds.minX) * xIndex) / gridSize,
        bounds.minY + ((bounds.maxY - bounds.minY) * yIndex) / gridSize,
      ];

      const containingPolygon = polygons.find((polygon) => isPointInPolygon(point, polygon));
      if (!containingPolygon) {
        continue;
      }

      const distance = getDistanceToPolygonEdges(point, containingPolygon);
      if (distance > bestDistance) {
        bestDistance = distance;
        bestPoint = point;
      }
    }
  }

  return bestPoint ?? allPoints[0] ?? EMPTY_CENTER;
}

function resolveGeometryCenter(polygons: Map3DCoordinate[][][]) {
  return resolveBestInteriorPoint(polygons) ?? resolveInteriorPoint(polygons);
}

function resolveLabelPosition(
  properties: Map3DFeatureProperties,
  polygons: Map3DCoordinate[][][],
  projection: ReturnType<typeof geoIdentity>,
): Map3DCoordinate {
  const interiorPoint = resolveInteriorPoint(polygons);
  if (isPointInPolygons(interiorPoint, polygons)) {
    return interiorPoint;
  }

  const preferred = isCoordinate(properties.centroid)
    ? properties.centroid
    : isCoordinate(properties.center)
      ? properties.center
      : null;

  if (preferred) {
    const projected = projectCoordinate(projection, preferred);
    if (projected) {
      return projected;
    }
  }

  return interiorPoint;
}

function resolveProjectionCenter(bounds: Map3DBounds | null): Map3DCoordinate {
  if (!bounds) {
    return EMPTY_CENTER;
  }

  return [
    (bounds.minX + bounds.maxX) / 2,
    (bounds.minY + bounds.maxY) / 2,
  ];
}

function isSameProjectedPoint(a?: Map3DCoordinate, b?: Map3DCoordinate) {
  return !!a && !!b && a[0] === b[0] && a[1] === b[1];
}

function closeRing(ring: Map3DCoordinate[]) {
  if (!ring.length) {
    return ring;
  }

  const first = ring[0];
  const last = ring[ring.length - 1];
  return isSameProjectedPoint(first, last) ? ring : [...ring, first];
}

function getBoundaryPointKey([x, y]: Map3DCoordinate) {
  return `${Math.round(x * BOUNDARY_POINT_PRECISION)},${Math.round(y * BOUNDARY_POINT_PRECISION)}`;
}

function getBoundarySegmentKey(fromKey: string, toKey: string) {
  return fromKey < toKey ? `${fromKey}|${toKey}` : `${toKey}|${fromKey}`;
}

function getOtherBoundaryPointKey(segment: BoundarySegment, pointKey: string) {
  return segment.fromKey === pointKey ? segment.toKey : segment.fromKey;
}

function getBoundarySegmentPoint(segment: BoundarySegment, pointKey: string) {
  return segment.fromKey === pointKey ? segment.from : segment.to;
}

function resolveOutlineRings(regions: Map3DProjectedRegion[]) {
  const segmentMap = new Map<string, BoundarySegment>();
  const pointMap = new Map<string, Map3DCoordinate>();

  regions.forEach((region) => {
    region.polygons.forEach(([outerRing]) => {
      closeRing(outerRing).forEach((point, index, ring) => {
        if (index === 0) {
          return;
        }

        const from = ring[index - 1];
        const to = point;
        const fromKey = getBoundaryPointKey(from);
        const toKey = getBoundaryPointKey(to);
        if (fromKey === toKey) {
          return;
        }

        const key = getBoundarySegmentKey(fromKey, toKey);
        const current = segmentMap.get(key);
        if (current) {
          current.count += 1;
          return;
        }

        pointMap.set(fromKey, from);
        pointMap.set(toKey, to);
        segmentMap.set(key, {
          key,
          from,
          to,
          fromKey,
          toKey,
          count: 1,
        });
      });
    });
  });

  const boundarySegments = Array.from(segmentMap.values()).filter((segment) => segment.count === 1);
  const adjacency = new Map<string, BoundarySegment[]>();
  boundarySegments.forEach((segment) => {
    adjacency.set(segment.fromKey, [...(adjacency.get(segment.fromKey) ?? []), segment]);
    adjacency.set(segment.toKey, [...(adjacency.get(segment.toKey) ?? []), segment]);
  });

  const usedSegments = new Set<string>();
  const rings: Map3DCoordinate[][] = [];

  boundarySegments.forEach((firstSegment) => {
    if (usedSegments.has(firstSegment.key)) {
      return;
    }

    const startKey = firstSegment.fromKey;
    let currentKey = firstSegment.toKey;
    let currentSegment = firstSegment;
    const ring: Map3DCoordinate[] = [firstSegment.from, firstSegment.to];
    usedSegments.add(firstSegment.key);

    while (currentKey !== startKey) {
      const nextSegment = adjacency.get(currentKey)?.find((segment) => !usedSegments.has(segment.key));
      if (!nextSegment) {
        break;
      }

      usedSegments.add(nextSegment.key);
      const nextKey = getOtherBoundaryPointKey(nextSegment, currentKey);
      ring.push(getBoundarySegmentPoint(nextSegment, nextKey));
      currentKey = nextKey;
      currentSegment = nextSegment;
    }

    if (currentKey === startKey && ring.length >= 4) {
      rings.push(ring);
      return;
    }

    // Keep partially reconstructed outlines usable when source boundaries are not perfectly shared.
    if (ring.length >= 4 && currentSegment) {
      rings.push(ring);
    }
  });

  return rings;
}

export function normalizeMap3DInput(value: unknown): Map3DFeatureCollection | null {
  if (!isRecord(value)) {
    return null;
  }

  if (value.type === 'FeatureCollection' && Array.isArray(value.features)) {
    return {
      type: 'FeatureCollection',
      features: value.features.filter(isFeature),
    };
  }

  if (isFeature(value)) {
    return {
      type: 'FeatureCollection',
      features: [value],
    };
  }

  return null;
}

export function projectMapRegions(
  input: Map3DFeatureCollection | null,
  options: ProjectMapRegionsOptions,
): Map3DProjectionResult {
  const warnings: string[] = [];

  if (!input?.features.length) {
    return {
      regions: [],
      outlineRings: [],
      bounds: null,
      geoBounds: null,
      center: EMPTY_CENTER,
      warnings: ['未传入可用的 GeoJSON FeatureCollection。'],
    };
  }

  const validFeatures = input.features.reduce<Array<{ feature: Map3DFeature; index: number }>>((list, feature, index) => {
    const sanitizedFeature = sanitizeFeatureGeometry(feature);
    if (!sanitizedFeature) {
      warnings.push(`第 ${index + 1} 个区域的 GeoJSON 几何数据无效，已跳过。`);
      return list;
    }

    list.push({ feature: sanitizedFeature, index });
    return list;
  }, []);

  if (!validFeatures.length) {
    return {
      regions: [],
      outlineRings: [],
      bounds: null,
      geoBounds: null,
      center: EMPTY_CENTER,
      warnings: warnings.length ? warnings : ['未传入可用的 GeoJSON FeatureCollection。'],
    };
  }

  const projection = geoIdentity().reflectY(true).fitExtent(PROJECTION_EXTENT, {
    type: 'FeatureCollection',
    features: validFeatures.map(({ feature }) => feature),
  });
  let bounds: Map3DBounds | null = null;
  let geoBounds: Map3DBounds | null = null;

  const regions = validFeatures.reduce<Map3DProjectedRegion[]>((list, entry) => {
    const { feature, index } = entry;
    const featureGeoBounds = resolveBounds(getPolygonSets(feature).flat(2) as Map3DCoordinate[]);
    geoBounds = mergeBounds(geoBounds, featureGeoBounds);
    const polygons = getPolygonSets(feature)
      .map((polygon) =>
        polygon
          .map((ring) =>
            ring
              .map((coordinate) => projectCoordinate(projection, coordinate))
              .filter((point): point is Map3DCoordinate => point !== null),
          )
          .filter((ring) => ring.length >= 4),
      )
      .filter((polygon) => polygon.length > 0);

    if (!polygons.length) {
      warnings.push(`第 ${index + 1} 个区域投影后没有有效面数据，已跳过。`);
      return list;
    }

    const regionBounds = resolveBounds(polygons.flat(2));
    if (!regionBounds) {
      warnings.push(`第 ${index + 1} 个区域投影后无法计算边界，已跳过。`);
      return list;
    }

    bounds = mergeBounds(bounds, regionBounds);

    const name = getRegionName(feature.properties, options.nameKey);
    list.push({
      key: `${index}-${name}`,
      index,
      name,
      feature,
      properties: feature.properties,
      polygons,
      labelPosition: resolveLabelPosition(feature.properties, polygons, projection),
      geometryCenter: resolveGeometryCenter(polygons),
    });

    return list;
  }, []);

  return {
    regions,
    outlineRings: resolveOutlineRings(regions),
    bounds,
    geoBounds,
    center: resolveProjectionCenter(bounds),
    warnings,
  };
}

export function buildTooltipRows(
  properties: Map3DFeatureProperties,
  options: BuildTooltipRowsOptions,
) {
  const fallbackRow = {
    key: options.nameKey,
    label: '名称',
    value: getRegionName(properties, options.nameKey),
  };

  if (!options.fields?.length) {
    return [fallbackRow];
  }

  const rows = options.fields.reduce((rows, field) => {
    const raw = properties[field.key];
    if (raw === undefined || raw === null || raw === '') {
      return rows;
    }

    rows.push({
      key: field.key,
      label: field.label ?? field.key,
      value: field.formatter ? field.formatter(raw, properties) : String(raw),
    });

    return rows;
  }, [] as Array<{ key: string; label: string; value: string }>);

  return rows.length ? rows : [fallbackRow];
}

export function buildMapRegionEvent(
  region: Map3DProjectedRegion,
  eventType: Map3DRegionEvent['eventType'],
): Map3DRegionEvent {
  return {
    eventType,
    feature: region.feature,
    properties: region.properties,
    index: region.index,
    name: region.name,
  };
}
