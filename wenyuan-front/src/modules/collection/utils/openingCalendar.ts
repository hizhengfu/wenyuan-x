import type { OpeningCalendarYear, OpeningException } from '../api/openingCalendar';
import { calendarDayKey } from './workingCalendar';
export function openingDayStatus(calendar: OpeningCalendarYear, date: number) {
  const key = calendarDayKey(date);
  const exception = calendar.exceptions.find((item) => calendarDayKey(item.date) === key);
  if (exception) return { kind: exception.open ? 'workday' : 'holiday', label: exception.open ? '临时开放' : '临时闭馆', open: exception.open };
  const weekday = new Date(date).getDay() || 7;
  const closed = calendar.weeklyClosures.some((rule) => key >= calendarDayKey(rule.startOn) && key <= calendarDayKey(rule.endOn) && rule.weekdays.includes(weekday));
  return { kind: closed ? 'holiday' : 'default', label: closed ? '固定休馆' : '开放', open: !closed };
}
export function setOpeningException(calendar: OpeningCalendarYear, date: number, value: Omit<OpeningException, 'date'> | null): OpeningCalendarYear {
  const parsed = new Date(date);
  if (!Number.isFinite(date) || parsed.getFullYear() !== calendar.year) throw new Error('请选择本年度日期');
  if (value && !value.reason.trim()) throw new Error('请填写临时安排原因');
  const normalized = new Date(parsed.getFullYear(), parsed.getMonth(), parsed.getDate()).getTime();
  const exceptions = calendar.exceptions.filter((item) => calendarDayKey(item.date) !== calendarDayKey(date));
  if (value) exceptions.push({ ...value, date: normalized, reason: value.reason.trim() });
  return { ...calendar, exceptions: exceptions.sort((a, b) => a.date - b.date) };
}
