export type PageDataTableColumnLike = {
  key?: string | number;
  title?: unknown;
  type?: string;
  show?: boolean;
  children?: PageDataTableColumnLike[];
  [key: string]: any;
};

export type PageDataTableColumnSettingOption = {
  label: string;
  value: string;
  disabled?: boolean;
};

function columnKey(column: PageDataTableColumnLike) {
  return column.key === undefined || column.key === null ? '' : String(column.key);
}

export function collectColumnSettingOptions(
  columns: readonly PageDataTableColumnLike[],
): PageDataTableColumnSettingOption[] {
  return columns.flatMap((column) => {
    if (column.show === false) return [];
    if (column.children?.length) return collectColumnSettingOptions(column.children);
    if (column.type === 'selection' || column.type === 'operate') return [];
    const value = columnKey(column);
    if (!value) return [];
    const label = typeof column.title === 'string' && column.title.trim() ? column.title.trim() : value;
    return [{ label, value }];
  });
}

export function normalizeVisibleColumnKeys(
  selected: readonly (string | number)[],
  options: readonly PageDataTableColumnSettingOption[],
  defaults: readonly (string | number)[],
) {
  const available = new Set(options.map((option) => option.value));
  const normalized = Array.from(new Set(selected.map(String))).filter((key) => available.has(key));
  if (normalized.length) return normalized;

  const normalizedDefaults = Array.from(new Set(defaults.map(String))).filter((key) => available.has(key));
  return normalizedDefaults.length ? normalizedDefaults : options.map((option) => option.value);
}

export function filterVisibleTableColumns<T extends PageDataTableColumnLike>(
  columns: readonly T[],
  configurableKeys: ReadonlySet<string>,
  visibleKeys: ReadonlySet<string>,
  alwaysVisibleKeys: ReadonlySet<string>,
): T[] {
  return columns.flatMap((column) => {
    if (column.show === false) return [];

    if (column.children?.length) {
      const children = filterVisibleTableColumns(column.children, configurableKeys, visibleKeys, alwaysVisibleKeys);
      return children.length ? [{ ...column, children } as T] : [];
    }

    const key = columnKey(column);
    const alwaysVisible =
      !key || column.type === 'selection' || column.type === 'operate' || alwaysVisibleKeys.has(key);
    const configurable = key && configurableKeys.has(key);
    return alwaysVisible || !configurable || visibleKeys.has(key) ? [{ ...column } as T] : [];
  });
}
