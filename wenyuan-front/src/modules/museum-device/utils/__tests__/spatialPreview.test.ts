import { describe, expect, it } from 'vitest';
import type { MuseumSpatialAnchorVo, MuseumSpatialDeviceVo, MuseumSpatialLocationVo } from '../../typings';
import { groupSpatialAnchors } from '../spatialAnchors';
import {
  findSpatialSourceLocation,
  preferredSpatialLocation,
  spatialLayerOptions,
  spatialLayerSelection,
  spatialPlanLocation,
} from '../spatialLayers';
import { resolveSpatialMetricIcon, resolveSpatialMetricIcons } from '../spatialMetricIcon';

const locations: MuseumSpatialLocationVo[] = [
  location('SITE', null, 0, 'SITE', 0),
  location('BUILDING-A', 'SITE', 1, 'BUILDING', 0),
  location('FLOOR-1', 'BUILDING-A', 2, 'FLOOR', 1),
  location('FLOOR-2', 'BUILDING-A', 2, 'FLOOR', 0),
  location('HALL-101', 'FLOOR-1', 3, 'SPACE', 1),
  location('WAREHOUSE-2', 'FLOOR-2', 3, 'WAREHOUSE', 1),
  location('CABINET-2', 'WAREHOUSE-2', 4, 'CABINET', 0),
  location('LAYER-2', 'CABINET-2', 5, 'LAYER', 0),
  {
    ...location('BOX-2', 'LAYER-2', 6, 'BOX', 0),
    sources: [{ sourceType: 'BUSINESS_MASTER', sourceCode: 'COL_WAREHOUSE', sourceLocationId: 'COL-BOX-2' }],
  },
];

describe('spatial preview layers', () => {
  it('restores building, floor and hall selectors from a location id', () => {
    const selection = spatialLayerSelection(locations, 'HALL-101');
    const options = spatialLayerOptions(locations, selection);

    expect(selection).toEqual({
      siteId: 'SITE',
      buildingId: 'BUILDING-A',
      floorId: 'FLOOR-1',
      spaceId: 'HALL-101',
    });
    expect(options.floors.map((item) => item.id)).toEqual(['FLOOR-1', 'FLOOR-2']);
    expect(options.spaces.map((item) => item.id)).toEqual(['HALL-101']);
  });

  it('prefers a floor plan when only venue id is supplied', () => {
    expect(preferredSpatialLocation(locations)?.id).toBe('FLOOR-1');
  });

  it('restores a business storage position and falls back to its nearest warehouse plan', () => {
    const target = findSpatialSourceLocation(locations, 'COL_WAREHOUSE', 'COL-BOX-2');
    const selection = spatialLayerSelection(locations, target?.id);
    const options = spatialLayerOptions(locations, selection);

    expect(target?.id).toBe('BOX-2');
    expect(selection.spaceId).toBe('BOX-2');
    expect(options.spaces.map((item) => item.id)).toEqual(['WAREHOUSE-2', 'CABINET-2', 'LAYER-2', 'BOX-2']);
    expect(spatialPlanLocation(locations, target?.id)?.id).toBe('WAREHOUSE-2');
  });

  it('does not treat a source location id as a canonical location id', () => {
    expect(preferredSpatialLocation(locations, 'COL-BOX-2')?.id).toBe('FLOOR-1');
    expect(findSpatialSourceLocation(locations, 'OTHER_SOURCE', 'COL-BOX-2')).toBeNull();
  });
});

describe('spatial metric icons', () => {
  it.each([
    ['TEMPERATURE', 'temperature'],
    ['HUMIDITY', 'humidity'],
    ['WATER_LEAK', 'water'],
    ['SMOKE', 'fire'],
    ['CO2', 'air'],
    ['DOOR_STATUS', 'door'],
    ['ILLUMINANCE', 'light'],
  ])('maps %s to %s', (key, expected) => {
    expect(resolveSpatialMetricIcon(device(key))).toBe(expected);
  });

  it('keeps temperature and humidity on one device as a composite icon', () => {
    const sensor = device('TEMPERATURE');
    sensor.latestMetrics.push({ key: 'HUMIDITY', numericValue: 52, occurredAt: 1, receivedAt: 1 });

    expect(resolveSpatialMetricIcons(sensor)).toEqual(['temperature', 'humidity']);
  });

  it('keeps the composite type for an offline temperature-humidity model without latest data', () => {
    const sensor = device('TEMPERATURE');
    sensor.latestMetrics = [];
    sensor.modelCode = 'MILESIGHT_EM300_TH_MODEL_V1';

    expect(resolveSpatialMetricIcons(sensor)).toEqual(['temperature', 'humidity']);
  });
});

describe('spatial anchor overlap groups', () => {
  const overlapping = [anchor('DEVICE-1', 0.5, 0.5), anchor('DEVICE-2', 0.505, 0.5)];

  it('groups visually overlapping devices while viewing', () => {
    const groups = groupSpatialAnchors(overlapping, 2400, 1600);

    expect(groups).toHaveLength(1);
    expect(groups[0].clustered).toBe(true);
    expect(groups[0].anchors.map((item) => item.targetId)).toEqual(['DEVICE-1', 'DEVICE-2']);
  });

  it('keeps every device independently draggable while editing', () => {
    expect(groupSpatialAnchors(overlapping, 2400, 1600, true)).toHaveLength(2);
  });

  it('does not merge location anchors or distant devices', () => {
    const groups = groupSpatialAnchors(
      [...overlapping.slice(0, 1), anchor('DEVICE-3', 0.8, 0.8), anchor('ROOM-1', 0.5, 0.5, 'LOCATION')],
      2400,
      1600,
    );

    expect(groups).toHaveLength(3);
    expect(groups.every((group) => !group.clustered)).toBe(true);
  });
});

function location(
  id: string,
  parentId: string | null,
  depth: number,
  type: string,
  planCount: number,
): MuseumSpatialLocationVo {
  return {
    id,
    orgId: 'VENUE-1',
    parentId,
    depth,
    code: id,
    name: id,
    type,
    planCount,
    deviceCount: 0,
    sources: [],
  };
}

function device(key: string): MuseumSpatialDeviceVo {
  return {
    id: 'DEVICE-1',
    code: 'DEVICE-1',
    name: '测试设备',
    modelCode: 'MODEL-1',
    state: 'NORMAL',
    latestMetrics: [
      {
        key,
        numericValue: 1,
        occurredAt: 1,
        receivedAt: 1,
      },
    ],
  };
}

function anchor(
  targetId: string,
  xRatio: number,
  yRatio: number,
  targetType: 'DEVICE' | 'LOCATION' = 'DEVICE',
): MuseumSpatialAnchorVo {
  return {
    id: `ANCHOR-${targetId}`,
    targetType,
    targetId,
    xRatio,
    yRatio,
    labelOffsetX: 0,
    labelOffsetY: 0,
    zIndex: 0,
  };
}
