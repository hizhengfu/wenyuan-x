export interface MetricDisplayValue {
  numericValue?: number | null;
  stringValue?: string | null;
  booleanValue?: boolean | null;
  unit?: string | null;
}

export interface MetricValueFormatOptions {
  emptyText?: string;
  maximumFractionDigits?: number;
  unitSeparator?: string;
  trueText?: string;
  falseText?: string;
}

const DEFAULT_EMPTY_TEXT = '--';

export function formatMetricNumber(value: unknown, options: MetricValueFormatOptions = {}) {
  if (value === null || value === undefined || value === '') return options.emptyText ?? DEFAULT_EMPTY_TEXT;
  const number = Number(value);
  if (!Number.isFinite(number)) return options.emptyText ?? DEFAULT_EMPTY_TEXT;
  return number.toLocaleString('zh-CN', {
    maximumFractionDigits: options.maximumFractionDigits ?? 3,
  });
}

export function formatMetricValue(value: MetricDisplayValue, options: MetricValueFormatOptions = {}) {
  const emptyText = options.emptyText ?? DEFAULT_EMPTY_TEXT;
  if (value.numericValue !== null && value.numericValue !== undefined) {
    if (Number.isFinite(Number(value.numericValue))) {
      return appendMetricUnit(formatMetricNumber(value.numericValue, options), value.unit, options.unitSeparator);
    }
  }
  if (value.booleanValue !== null && value.booleanValue !== undefined) {
    return value.booleanValue ? (options.trueText ?? '是') : (options.falseText ?? '否');
  }
  if (value.stringValue !== null && value.stringValue !== undefined) {
    const stringValue = String(value.stringValue);
    return stringValue ? appendMetricUnit(stringValue, value.unit, options.unitSeparator) : emptyText;
  }
  return emptyText;
}

function appendMetricUnit(value: string, unit?: string | null, separator = ' ') {
  return unit ? `${value}${separator}${unit}` : value;
}
