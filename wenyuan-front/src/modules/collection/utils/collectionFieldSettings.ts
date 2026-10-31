export type CollectionFieldOption = {
  label: string;
  value: string;
};

export function normalizeFieldKeys(
  selected: readonly string[],
  options: readonly CollectionFieldOption[],
  defaults: readonly string[],
) {
  const available = new Set(options.map((option) => option.value));
  const normalized = Array.from(new Set(selected)).filter((key) => available.has(key));
  return normalized.length ? normalized : [...defaults];
}

export function parseStoredFieldKeys(
  raw: string | null | undefined,
  options: readonly CollectionFieldOption[],
  defaults: readonly string[],
) {
  if (!raw) return [...defaults];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? normalizeFieldKeys(parsed.map(String), options, defaults) : [...defaults];
  } catch {
    return [...defaults];
  }
}

export function stringifyFieldKeys(keys: readonly string[]) {
  return JSON.stringify([...keys]);
}
