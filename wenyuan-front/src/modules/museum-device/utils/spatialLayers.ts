import type { MuseumSpatialLocationVo } from '../typings';

export type SpatialLocationLevel = 'SITE' | 'BUILDING' | 'FLOOR' | 'SPACE' | 'OTHER';

export interface SpatialLayerSelection {
  siteId: string | null;
  buildingId: string | null;
  floorId: string | null;
  spaceId: string | null;
}

export interface SpatialLayerOptions {
  sites: MuseumSpatialLocationVo[];
  buildings: MuseumSpatialLocationVo[];
  floors: MuseumSpatialLocationVo[];
  spaces: MuseumSpatialLocationVo[];
}

const SITE_TYPES = new Set(['SITE', 'VENUE', 'MUSEUM', 'CAMPUS']);
const BUILDING_TYPES = new Set(['BUILDING', 'BUILD']);
const FLOOR_TYPES = new Set(['FLOOR', 'STOREY', 'STORY', 'LEVEL']);
const SPACE_TYPES = new Set([
  'SPACE',
  'HALL',
  'GALLERY',
  'ROOM',
  'WAREHOUSE',
  'ZONE',
  'AREA',
  'CABINET',
  'LAYER',
  'BOX',
  'EXHIBITION_HALL',
  'DISPLAY_AREA',
  'SHOWCASE',
  'STORAGE',
  'STOREROOM',
  'DEPOT',
  'POSITION',
  'SHELF',
  'BIN',
]);

export function spatialLocationLevel(location: MuseumSpatialLocationVo): SpatialLocationLevel {
  const type = String(location.type || '').trim().toUpperCase();
  if (SITE_TYPES.has(type) || location.depth === 0) return 'SITE';
  if (BUILDING_TYPES.has(type)) return 'BUILDING';
  if (FLOOR_TYPES.has(type)) return 'FLOOR';
  if (SPACE_TYPES.has(type)) return 'SPACE';
  return 'OTHER';
}

export function spatialLocationAncestors(
  locations: MuseumSpatialLocationVo[],
  locationId: string,
): MuseumSpatialLocationVo[] {
  const byId = new Map(locations.map((item) => [item.id, item]));
  const chain: MuseumSpatialLocationVo[] = [];
  const visited = new Set<string>();
  let current = byId.get(locationId);
  while (current && !visited.has(current.id)) {
    chain.unshift(current);
    visited.add(current.id);
    current = current.parentId ? byId.get(current.parentId) : undefined;
  }
  return chain;
}

export function isSpatialDescendant(
  locations: MuseumSpatialLocationVo[],
  location: MuseumSpatialLocationVo,
  ancestorId: string | null,
): boolean {
  if (!ancestorId) return true;
  if (location.id === ancestorId) return false;
  return spatialLocationAncestors(locations, location.id).some((item) => item.id === ancestorId);
}

export function spatialLayerSelection(
  locations: MuseumSpatialLocationVo[],
  locationId?: string | null,
): SpatialLayerSelection {
  const selection: SpatialLayerSelection = { siteId: null, buildingId: null, floorId: null, spaceId: null };
  if (!locationId) return selection;
  spatialLocationAncestors(locations, locationId).forEach((location) => {
    const level = spatialLocationLevel(location);
    if (level === 'SITE') selection.siteId = location.id;
    if (level === 'BUILDING') selection.buildingId = location.id;
    if (level === 'FLOOR') selection.floorId = location.id;
    if (level === 'SPACE') selection.spaceId = location.id;
  });
  return selection;
}

export function spatialLayerOptions(
  locations: MuseumSpatialLocationVo[],
  selection: SpatialLayerSelection,
): SpatialLayerOptions {
  const sites = locations.filter((item) => spatialLocationLevel(item) === 'SITE');
  const buildings = locations.filter(
    (item) => spatialLocationLevel(item) === 'BUILDING' && isSpatialDescendant(locations, item, selection.siteId),
  );
  const floorRoot = selection.buildingId || selection.siteId;
  const floors = locations.filter(
    (item) => spatialLocationLevel(item) === 'FLOOR' && isSpatialDescendant(locations, item, floorRoot),
  );
  const spaceRoot = selection.floorId || selection.buildingId || selection.siteId;
  const spaces = locations.filter(
    (item) => spatialLocationLevel(item) === 'SPACE' && isSpatialDescendant(locations, item, spaceRoot),
  );
  return { sites, buildings, floors, spaces };
}

export function deepestSpatialLocationId(selection: SpatialLayerSelection): string | null {
  return selection.spaceId || selection.floorId || selection.buildingId || selection.siteId;
}

export function findSpatialLocation(
  locations: MuseumSpatialLocationVo[],
  locationId?: string | null,
): MuseumSpatialLocationVo | null {
  if (!locationId) return null;
  return locations.find((item) => item.id === locationId) || null;
}

export function findSpatialSourceLocation(
  locations: MuseumSpatialLocationVo[],
  sourceCode?: string | null,
  sourceLocationId?: string | null,
): MuseumSpatialLocationVo | null {
  const normalizedSourceCode = sourceCode?.trim().toUpperCase();
  const normalizedSourceLocationId = sourceLocationId?.trim();
  if (!normalizedSourceCode || !normalizedSourceLocationId) return null;
  return (
    locations.find((item) =>
      (item.sources || []).some(
        (source) =>
          source.sourceCode.toUpperCase() === normalizedSourceCode &&
          source.sourceLocationId === normalizedSourceLocationId,
      ),
    ) || null
  );
}

/** 优先显示当前位置的图；库位未单独配置图时，回退到最近的上级库房/楼层图。 */
export function spatialPlanLocation(
  locations: MuseumSpatialLocationVo[],
  locationId?: string | null,
): MuseumSpatialLocationVo | null {
  const target = findSpatialLocation(locations, locationId);
  if (!target) return null;
  const ancestorWithPlan = [...spatialLocationAncestors(locations, target.id)]
    .reverse()
    .find((item) => item.planCount > 0);
  if (ancestorWithPlan) return ancestorWithPlan;

  return (
    locations
      .filter((item) => item.planCount > 0 && isSpatialDescendant(locations, item, target.id))
      .sort((left, right) => left.depth - right.depth || String(left.path || left.name).localeCompare(String(right.path || right.name)))[0] ||
    target
  );
}

export function preferredSpatialLocation(
  locations: MuseumSpatialLocationVo[],
  requestedId?: string | null,
): MuseumSpatialLocationVo | null {
  const requested = findSpatialLocation(locations, requestedId);
  if (requested) return requested;
  const withPlan = locations.filter((item) => item.planCount > 0);
  return (
    withPlan.find((item) => spatialLocationLevel(item) === 'FLOOR') ||
    withPlan.find((item) => spatialLocationLevel(item) === 'SPACE') ||
    withPlan.find((item) => spatialLocationLevel(item) === 'BUILDING') ||
    withPlan[0] ||
    locations[0] ||
    null
  );
}
