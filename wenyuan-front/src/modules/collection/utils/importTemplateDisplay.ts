export type ImportDictOption = {
  label: string;
  value: string;
};

export type ImportTemplateDisplayColumn = {
  dictType?: string | null;
  sample?: string | null;
};

export type ImportDictOptions = Record<string, ImportDictOption[]>;

export function formatImportDictOptions(dictType: string | null | undefined, optionsByType: ImportDictOptions) {
  if (!dictType) return '--';
  const labels = optionsByType[dictType]?.map((item) => item.label).filter(Boolean) ?? [];
  return labels.length ? labels.join(' / ') : '未配置字典值';
}

export function formatImportSample(row: ImportTemplateDisplayColumn, optionsByType: ImportDictOptions) {
  const sample = row.sample?.trim();
  if (!sample) return '--';
  if (!row.dictType) return sample;
  const options = optionsByType[row.dictType] ?? [];
  const option = options.find((item) => item.value === sample || item.label === sample);
  return option?.label || sample;
}
