/** 藏品金额的接口单位仍为分；界面统一按元录入。 */
export function centsToYuan(value: number | null | undefined): number | null {
  return value == null ? null : Number(value) / 100;
}

export function yuanToCents(value: number | null | undefined): number | undefined {
  if (value == null) return undefined;
  if (!Number.isSafeInteger(value) || value < 0 || value > Math.floor(Number.MAX_SAFE_INTEGER / 100)) {
    throw new RangeError('金额须为非负整数元');
  }
  return value * 100;
}

/** 已存的非整元旧值可原样回显；新输入仍限制为整数元。 */
export function yuanInputPrecision(cents: number | null | undefined): number {
  return cents != null && cents % 100 !== 0 ? 2 : 0;
}

export function yuanFormToCents(value: number | null, previous?: number | null): number | undefined {
  if (value == null) return undefined;
  if (Number.isSafeInteger(value)) return yuanToCents(value);
  if (previous != null && Math.round(value * 100) === previous) return previous;
  return previous ?? undefined;
}

export function formatYuan(value: number | null | undefined): string {
  if (value == null) return '--';
  const yuan = centsToYuan(value)!;
  return `¥${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: yuan % 1 === 0 ? 0 : 2,
  }).format(yuan)} 元`;
}

export function yuanImportColumn<T extends { field: string; title: string; sample?: string }>(
  column: T,
  moneyFields: ReadonlySet<string>,
): T {
  if (!moneyFields.has(column.field)) return column;
  return {
    ...column,
    title: column.title.replace('（分）', '（元）'),
    sample: column.sample == null ? undefined : String(centsToYuan(Number(column.sample))),
  };
}

export function parseImportAmount(value: string | number, header: string): string | number {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return value;
  return header.includes('（元）') ? yuanToCents(amount)! : amount;
}
