export type CollectionCorrectionField =
  | 'name'
  | 'level'
  | 'source'
  | 'acquireDate'
  | 'acquireDateRange'
  | 'acquireYear';

export type CollectionCorrectionValue = string | number | null;

export type CollectionCorrectionRow = Partial<Record<CollectionCorrectionField, CollectionCorrectionValue | undefined>>;

export const correctionFieldOptions: { label: string; value: CollectionCorrectionField }[] = [
  { label: '名称', value: 'name' },
  { label: '藏品级别', value: 'level' },
  { label: '来源方式', value: 'source' },
  { label: '取得日期', value: 'acquireDate' },
  { label: '取得时间范围', value: 'acquireDateRange' },
  { label: '取得年度', value: 'acquireYear' },
];

export function initialCorrectionValue(
  row: CollectionCorrectionRow | null | undefined,
  field: CollectionCorrectionField,
) {
  const value = row?.[field];
  if (field === 'acquireDate') return localDateTimestamp(value);
  return String(value ?? '');
}

export function normalizeCorrectionValue(field: CollectionCorrectionField, value: unknown) {
  if (field === 'acquireDate') {
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value.trim())) return value.trim();
    const timestamp = localDateTimestamp(value);
    if (timestamp === null) return '';
    const date = new Date(timestamp);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
  return String(value ?? '').trim();
}

function localDateTimestamp(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string') return null;
  const clean = value.trim();
  const dateParts = clean.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (dateParts) {
    return new Date(Number(dateParts[1]), Number(dateParts[2]) - 1, Number(dateParts[3])).getTime();
  }
  const timestamp = Number(clean);
  return Number.isFinite(timestamp) ? timestamp : null;
}
