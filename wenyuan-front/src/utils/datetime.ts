import zhCn from 'dayjs/locale/zh-cn'
import relativeTime from 'dayjs/plugin/relativeTime'
import dayjs from 'dayjs';

dayjs.locale(zhCn)
dayjs.extend(relativeTime)


/**
 * 格式化时间
 * */
export function formatDateTime(dateTime: unknown, format: string = 'YYYY-MM-DD HH:mm:ss') {
  const normalized = normalizeDateValue(dateTime)
  if (!normalized) return '--'
  const date = dayjs(normalized)
  return date.isValid() ? date.format(format) : '--'
}

export function formatDateValue(dateTime: unknown, format: string = 'YYYY-MM-DD') {
  const normalized = normalizeDateValue(dateTime)
  if (!normalized) return '--'
  const date = dayjs(normalized)
  return date.isValid() ? date.format(format) : '--'
}

/**
 * 格式化后端 LocalDate。LocalDate 数字值统一是毫秒，不使用秒/毫秒猜测，
 * 避免 1970 年前的负数和纪元附近的小数值被错误换算。
 */
export function formatLocalDateValue(dateTime: unknown, format: string = 'YYYY-MM-DD') {
  const normalized = normalizeLocalDateValue(dateTime)
  if (normalized === null) return '--'
  const date = dayjs(normalized)
  return date.isValid() ? date.format(format) : '--'
}

export function hasLocalDateValue(value: unknown) {
  return value !== undefined && value !== null && value !== ''
}

function normalizeLocalDateValue(value: unknown): string | number | Date | null {
  if (value === undefined || value === null || value === '') return null

  if (Array.isArray(value)) {
    const [year, month = 1, day = 1] = value.map(Number)
    if (!year || Number.isNaN(year)) return null
    return new Date(year, month - 1, day)
  }

  if (typeof value === 'number') return Number.isFinite(value) ? value : null

  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return null
    if (/^-?\d+$/.test(text)) {
      const timestamp = Number(text)
      return Number.isFinite(timestamp) ? timestamp : null
    }
    return text
  }

  return value instanceof Date ? value : null
}

function normalizeDateValue(value: unknown): string | number | Date | null {
  if (value === undefined || value === null || value === '') return null

  if (Array.isArray(value)) {
    const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0, millisecond = 0] = value.map(Number)
    if (!year || Number.isNaN(year)) return null
    return new Date(year, month - 1, day, hour, minute, second, millisecond)
  }

  if (typeof value === 'number') {
    return normalizeTimestamp(value)
  }

  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return null
    if (/^-?\d+$/.test(text)) {
      return normalizeTimestamp(Number(text))
    }
    return text
  }

  if (value instanceof Date) return value

  return null
}

function normalizeTimestamp(value: number) {
  if (!Number.isFinite(value)) return null
  return Math.abs(value) < 10_000_000_000 ? value * 1000 : value
}

/**
 * 友好显示时间
 * */
export function fromTimeNow(dateTime?: string) {
  if (!dateTime) return '--'
  return dayjs(dateTime).fromNow()
}
