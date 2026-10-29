export type ResourceGrantScopeType = 'SELECTED' | 'FILTER' | 'CATEGORY' | 'IMPORT';

export function normalizeResourceIds(resourceIds: readonly unknown[] | null | undefined): string[] {
  return [
    ...new Set(
      (resourceIds || [])
        .filter((id) => id !== null && id !== undefined)
        .map((id) => String(id).trim())
        .filter(Boolean),
    ),
  ];
}

export function resourceIdsForGrantScope(
  scopeType: ResourceGrantScopeType,
  selectedResourceIds: readonly unknown[],
): string[] {
  return scopeType === 'SELECTED' ? normalizeResourceIds(selectedResourceIds) : [];
}

export function initialResourceGrantScope(selectedResourceIds: readonly unknown[]): ResourceGrantScopeType {
  return normalizeResourceIds(selectedResourceIds).length ? 'SELECTED' : 'FILTER';
}
