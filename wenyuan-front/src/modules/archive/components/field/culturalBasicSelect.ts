export function buildCulturalBasicSelectQuery(
  baseParams: Record<string, unknown> | null | undefined,
  requestParams: Record<string, unknown> = {},
) {
  return {
    ...(baseParams || {}),
    ...requestParams,
  };
}

export function buildCulturalBasicSelectInitialParams(
  value: string | number | null | undefined,
  baseParams: Record<string, unknown> | null | undefined,
) {
  if (baseParams && Object.keys(baseParams).length > 0) {
    return {};
  }
  return value === null || value === undefined || value === '' ? {} : { id: value };
}

export function buildCulturalBasicSelectSelectedParams(
  value: string | number | null | undefined,
  baseParams: Record<string, unknown> | null | undefined,
) {
  if (!baseParams || Object.keys(baseParams).length === 0) {
    return null;
  }
  return value === null || value === undefined || value === '' ? null : { id: value };
}

export function mergeCulturalBasicSelectOptions(
  ...optionGroups: Array<Array<Record<string, unknown>> | null | undefined>
) {
  const merged: Array<Record<string, unknown>> = [];
  const seenIds = new Set<string>();

  optionGroups.forEach((group) => {
    (group || []).forEach((option) => {
      const id = `${option.id ?? ''}`;
      if (!id || seenIds.has(id)) {
        return;
      }
      seenIds.add(id);
      merged.push(option);
    });
  });

  return merged;
}
