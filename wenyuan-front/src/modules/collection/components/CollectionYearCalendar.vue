<template>
            <div class="calendar-year-grid">
              <section v-for="item in yearMonths" :key="item.month" class="calendar-mini-month" :aria-label="`${year}年${item.month}月`">
                <NButton text class="calendar-month-title" @click="emit('month', item.month)">{{ item.month }} 月</NButton>
                <div class="calendar-mini-week"><span v-for="weekday in weekdayLabels" :key="weekday">{{ weekday }}</span></div>
                <div class="calendar-mini-days">
                  <template v-for="(day, index) in item.days" :key="index">
                    <span v-if="day === null" class="calendar-mini-blank" />
                    <button v-else type="button" class="calendar-mini-day" :class="[status(day).kind, { selected: selectedDate !== null && calendarDayKey(day) === calendarDayKey(selectedDate) }]"
                      :disabled="disabled" :title="`${calendarDayKey(day)} ${status(day).label}`" :aria-label="`${calendarDayKey(day)} ${status(day).label}`" :aria-pressed="selectedDate !== null && calendarDayKey(day) === calendarDayKey(selectedDate)" @click="emit('select', day)">
                      <span>{{ new Date(day).getDate() }}</span><small v-if="status(day).kind === 'holiday'">休</small><small v-else-if="status(day).kind === 'workday'">班</small>
                    </button>
                  </template>
                </div>
              </section>
            </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { calendarDayKey, calendarMonthDays } from '../utils/workingCalendar';
const props = defineProps<{ year: number; selectedDate: number | null; disabled?: boolean; status: (date: number) => { kind: string; label: string } }>();
const emit = defineEmits<{ select: [date: number]; month: [month: number] }>();
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日'];
const yearMonths = computed(() => Array.from({ length: 12 }, (_, index) => ({ month: index + 1, days: calendarMonthDays(props.year, index + 1) })));
</script>
<style scoped>
.calendar-year-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; }
.calendar-mini-month { border: 1px solid var(--border-color, #e5e7eb); border-radius: 6px; padding: 10px; }
.calendar-month-title { margin-bottom: 8px; font-weight: 600; }
.calendar-mini-week, .calendar-mini-days { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 3px; }
.calendar-mini-week { text-align: center; color: var(--text-color-3, #767c82); font-size: 12px; margin-bottom: 6px; }
.calendar-mini-day { position: relative; border: 1px solid transparent; border-radius: 4px; min-height: 30px; background: transparent; color: var(--text-color, #333); cursor: pointer; font: inherit; font-size: 12px; padding: 3px 1px; }
.calendar-mini-day:hover { background: var(--table-color-hover, rgba(128,128,128,.08)); }
.calendar-mini-day:focus-visible, .calendar-mini-day.selected { outline: 2px solid var(--primary-color, #18a058); outline-offset: -2px; }
.calendar-mini-day.holiday { color: var(--error-color, #d03050); background: rgba(208,48,80,.08); }
.calendar-mini-day.workday { color: var(--warning-color, #b36b00); background: rgba(240,160,32,.1); }
.calendar-mini-day.weekend { color: var(--info-color, #2080f0); }
.calendar-mini-day small { font-size: 9px; margin-left: 1px; }
.calendar-mini-day:disabled { cursor: not-allowed; opacity: .6; }
</style>
