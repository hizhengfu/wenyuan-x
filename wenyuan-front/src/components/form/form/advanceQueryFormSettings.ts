export type AdvanceQueryFormDiscoveredOption = {
  label: string;
  value: string;
};

export type AdvanceQueryFormClearContext = {
  params?: Record<string, any>;
};

export type AdvanceQueryFormSettingOption = AdvanceQueryFormDiscoveredOption & {
  clearPaths?: string[];
  defaultVisible?: boolean;
  disabled?: boolean;
  onClear?: (context: AdvanceQueryFormClearContext) => boolean | void;
};

export type AdvanceQueryFormConfiguredOption = Partial<Omit<AdvanceQueryFormSettingOption, 'value'>> & {
  value: string;
};

export type AdvanceQueryFormVisibilityChange = {
  visibleKeys: string[];
  hiddenKeys: string[];
  options: AdvanceQueryFormSettingOption[];
  reason: 'initialize' | 'sync' | 'user';
};

export function mergeQuerySettingOptions(
  discovered: readonly AdvanceQueryFormDiscoveredOption[],
  configured: readonly AdvanceQueryFormConfiguredOption[],
  alwaysVisibleKeys: readonly string[] = [],
) {
  const configuredMap = new Map(
    configured.filter((option) => Boolean(option.value)).map((option) => [String(option.value), option]),
  );
  const alwaysVisible = new Set(alwaysVisibleKeys.map(String));

  return discovered.map<AdvanceQueryFormSettingOption>((option) => {
    const override = configuredMap.get(option.value);
    return {
      ...option,
      ...override,
      label: override?.label || option.label,
      value: option.value,
      clearPaths: override?.clearPaths?.length ? override.clearPaths.map(String) : [option.value],
      disabled: Boolean(override?.disabled || alwaysVisible.has(option.value)),
    };
  });
}

export function defaultVisibleQueryKeys(
  options: readonly AdvanceQueryFormSettingOption[],
  configuredDefaults: readonly string[] = [],
) {
  if (configuredDefaults.length) return configuredDefaults.map(String);
  return options.filter((option) => option.defaultVisible !== false).map((option) => option.value);
}

export function normalizeVisibleQueryKeys(
  selected: readonly string[],
  options: readonly AdvanceQueryFormSettingOption[],
  defaults: readonly string[],
) {
  const available = new Set(options.map((option) => option.value));
  const normalized = Array.from(new Set(selected.map(String))).filter((key) => available.has(key));
  const fallback = Array.from(new Set(defaults.map(String))).filter((key) => available.has(key));
  return normalized.length ? normalized : fallback;
}

export function parseStoredVisibleQueryKeys(
  raw: string | null | undefined,
  options: readonly AdvanceQueryFormSettingOption[],
  defaults: readonly string[],
) {
  if (!raw) return normalizeVisibleQueryKeys(defaults, options, defaults);
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed)
      ? normalizeVisibleQueryKeys(parsed.map(String), options, defaults)
      : normalizeVisibleQueryKeys(defaults, options, defaults);
  } catch {
    return normalizeVisibleQueryKeys(defaults, options, defaults);
  }
}
