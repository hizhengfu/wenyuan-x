import type { CalendarYear } from '../api/calendar';

export type WorkingDayKind = 'holiday' | 'workday' | 'default';
export function calendarDayKey(value: number): string {
  const day = new Date(value);
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`;
}
export function calendarDayKind(calendar: CalendarYear, value: number): WorkingDayKind {
  const key = calendarDayKey(value);
  if (calendar.extraWorkdays.some((day) => calendarDayKey(day) === key)) return 'workday';
  if (calendar.holidays.some((day) => calendarDayKey(day) === key)) return 'holiday';
  return 'default';
}
export function setCalendarDay(calendar: CalendarYear, value: number, kind: WorkingDayKind): CalendarYear {
  const day = new Date(value);
  if (!Number.isFinite(value) || day.getFullYear() !== calendar.year) throw new Error('请选择本年度日期');
  const normalized = new Date(day.getFullYear(), day.getMonth(), day.getDate()).getTime();
  const key = calendarDayKey(normalized);
  const holidays = calendar.holidays.filter((item) => calendarDayKey(item) !== key);
  const extraWorkdays = calendar.extraWorkdays.filter((item) => calendarDayKey(item) !== key);
  if (kind === 'holiday') holidays.push(normalized);
  if (kind === 'workday') extraWorkdays.push(normalized);
  return { ...calendar, holidays: holidays.sort((a, b) => a - b), extraWorkdays: extraWorkdays.sort((a, b) => a - b) };
}

export function calendarMonthDays(year: number, month: number): Array<number | null> {
  const start = new Date(year, month - 1, 1);
  const count = new Date(year, month, 0).getDate();
  const leading = (start.getDay() + 6) % 7;
  const cells: Array<number | null> = Array.from({ length: leading }, () => null);
  for (let date = 1; date <= count; date++) cells.push(new Date(year, month - 1, date).getTime());
  while (cells.length % 7) cells.push(null);
  return cells;
}

export function calendarWeekdayDates(calendar: CalendarYear, range: [number, number], weekdays: number[], preserveSpecial: boolean): number[] {
  if (range.some((value) => !Number.isFinite(value) || new Date(value).getFullYear() !== calendar.year)) throw new Error('日期范围必须在本年度内');
  const start = new Date(range[0]), end = new Date(range[1]);
  start.setHours(0, 0, 0, 0);
  end.setHours(0, 0, 0, 0);
  if (start > end) throw new Error('结束日期不能早于开始日期');
  const dates: number[] = [];
  for (const current = new Date(start); current <= end; current.setDate(current.getDate() + 1)) {
    if (weekdays.includes(current.getDay()) && (!preserveSpecial || calendarDayKind(calendar, current.getTime()) === 'default')) dates.push(current.getTime());
  }
  return dates;
}
export function setCalendarWeekdays(calendar: CalendarYear, range: [number, number], weekdays: number[], kind: WorkingDayKind, preserveSpecial = true): CalendarYear {
  return calendarWeekdayDates(calendar, range, weekdays, preserveSpecial)
    .reduce((result, date) => setCalendarDay(result, date, kind), calendar);
}
