import { describe, expect, it } from 'vitest';
import {
  buildCulturalBasicSelectInitialParams,
  buildCulturalBasicSelectQuery,
  buildCulturalBasicSelectSelectedParams,
  mergeCulturalBasicSelectOptions,
} from '../culturalBasicSelect';

describe('cultural basic select query', () => {
  it('keeps the exact area filter during initial value loading', () => {
    expect(buildCulturalBasicSelectQuery({ areaId: '360102' }, { id: 'heritage-1' })).toEqual({
      areaId: '360102',
      id: 'heritage-1',
    });
  });

  it('keeps the exact area filter during keyword searching', () => {
    expect(buildCulturalBasicSelectQuery({ areaId: '360102' }, { publishName: '城墙' })).toEqual({
      areaId: '360102',
      publishName: '城墙',
    });
  });

  it('loads all candidates in the area even when a value is already selected', () => {
    expect(buildCulturalBasicSelectInitialParams('heritage-1', { areaId: '360102' })).toEqual({});
  });

  it('queries the selected value within the same area filter for its display name', () => {
    expect(buildCulturalBasicSelectSelectedParams('heritage-1', { areaIds: ['360102'] })).toEqual({
      id: 'heritage-1',
    });
    expect(buildCulturalBasicSelectSelectedParams('', { areaIds: ['360102'] })).toBeNull();
    expect(buildCulturalBasicSelectSelectedParams('heritage-1', {})).toBeNull();
  });

  it('keeps the existing selected-value lookup for callers without filter params', () => {
    expect(buildCulturalBasicSelectInitialParams('heritage-1', {})).toEqual({ id: 'heritage-1' });
  });

  it('keeps the queried selected option and removes duplicates from area candidates', () => {
    const selected = { id: 'heritage-1', publishName: '名称一' };
    const other = { id: 'heritage-2', publishName: '名称二' };
    expect(mergeCulturalBasicSelectOptions([selected], [selected, other])).toEqual([selected, other]);
  });

});
