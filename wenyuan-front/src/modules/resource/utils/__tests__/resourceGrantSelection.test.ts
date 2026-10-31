import { describe, expect, it } from 'vitest';
import { initialResourceGrantScope, normalizeResourceIds, resourceIdsForGrantScope } from '../resourceGrantSelection';

describe('resource grant selection', () => {
  it('normalizes the checked resource keys before opening the workbench', () => {
    expect(normalizeResourceIds([' resource-1 ', 'resource-2', 'resource-1', '', null])).toEqual([
      'resource-1',
      'resource-2',
    ]);
  });

  it('passes checked resource ids only for the selected-resource scope', () => {
    const selectedIds = [' resource-1 ', 'resource-2', 'resource-1'];
    const requestIds = resourceIdsForGrantScope('SELECTED', selectedIds);
    selectedIds.push('resource-3');

    expect(requestIds).toEqual(['resource-1', 'resource-2']);
    expect(resourceIdsForGrantScope('FILTER', ['resource-1', 'resource-2'])).toEqual([]);
    expect(resourceIdsForGrantScope('CATEGORY', ['resource-1', 'resource-2'])).toEqual([]);
    expect(resourceIdsForGrantScope('IMPORT', ['resource-1', 'resource-2'])).toEqual([]);
  });

  it('defaults to selected scope only when at least one valid resource id exists', () => {
    expect(initialResourceGrantScope(['resource-1'])).toBe('SELECTED');
    expect(initialResourceGrantScope([])).toBe('FILTER');
    expect(initialResourceGrantScope([' ', null])).toBe('FILTER');
  });
});
