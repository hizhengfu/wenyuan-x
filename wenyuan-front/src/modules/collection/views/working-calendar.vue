<template>
  <NCard title="法定工作日历">
    <NSpace vertical :size="16">
      <NAlert type="info">用于退出公示和借展报批、备案期限。系统已核验 2026 年全国节假日与调休；其他年度或本地区另有规定时，请依据正式通知登记。每次保存均保留原版本和通知附件。</NAlert>
      <div class="calendar-toolbar">
        <NForm inline label-placement="left" :show-feedback="false">
          <NFormItem label="收藏单位" required><NSelect v-model:value="orgId" :options="orgOptions" filterable placeholder="选择适用单位" style="width:240px" :disabled="saving" /></NFormItem>
          <NFormItem label="年度" required><NInputNumber v-model:value="year" :min="1900" :max="2200" :precision="0" :disabled="saving" style="width:130px" /></NFormItem>
        </NForm>
        <NSpace>
          <NButton :disabled="!orgId || !year || saving" :loading="loading" @click="load">加载日历</NButton>
          <NButton :disabled="!orgId || !year || loading || saving" @click="newCalendar">登记新日历</NButton>
        </NSpace>
      </div>
      <template v-if="form">
        <div class="working-calendar-layout">
          <NCard size="small" title="日期安排" class="calendar-main">
            <template #header-extra>
              <NSpace size="small">
                <NRadioGroup v-model:value="viewMode" size="small" aria-label="日历视图"><NRadioButton value="year">全年</NRadioButton><NRadioButton value="month">单月</NRadioButton></NRadioGroup>
                <NSelect v-if="viewMode === 'month'" v-model:value="month" :options="monthOptions" aria-label="查看月份" style="width:100px" />
              </NSpace>
            </template>
            <NSpace size="small" class="calendar-legend" wrap>
              <NTag size="small" :bordered="false">工作日</NTag>
              <NTag size="small" type="info" :bordered="false">周末休息</NTag>
              <NTag size="small" type="error" :bordered="false">休假日 {{ form.holidays.length }} 天</NTag>
              <NTag size="small" type="warning" :bordered="false">调休工作日 {{ form.extraWorkdays.length }} 天</NTag>
            </NSpace>
            <CollectionYearCalendar v-if="viewMode === 'year'" :year="form.year" :selected-date="selectedDate" :disabled="saving" :status="dateStatus" @select="selectDate" @month="month = $event; viewMode = 'month'" />
            <NCalendar v-else :key="`${form.year}-${month}-${panelRevision}`" :value="selectedDate" :default-value="monthStart"
              :is-date-disabled="isDateDisabled" @update:value="selectDate" @panel-change="changeMonth">
              <template #default="{ year: cellYear, month: cellMonth, date: cellDate }">
                <span class="calendar-day-status" :class="dayStatus(cellYear, cellMonth, cellDate).kind">{{ dayStatus(cellYear, cellMonth, cellDate).label }}</span>
              </template>
            </NCalendar>
            <NText depth="3" class="calendar-hint">点击日期后在右侧调整类型。未特别标记的周一至周五为工作日，周六、周日为休息日。</NText>
          </NCard>
          <div class="calendar-sidebar">
            <NCard size="small" title="调整日期">
              <NSpace vertical :size="12">
                <template v-if="selectedDate !== null">
                  <NText strong>{{ calendarDayKey(selectedDate) }}</NText>
                  <NRadioGroup v-model:value="selectedKind" :disabled="saving" name="working-day-kind">
                    <NSpace vertical><NRadio value="holiday">休假日</NRadio><NRadio value="workday">调休工作日</NRadio><NRadio value="default">恢复默认（按星期）</NRadio></NSpace>
                  </NRadioGroup>
                  <NButton type="primary" secondary :disabled="saving" @click="applySelectedDate">应用到此日期</NButton>
                  <NText depth="3">日期调整尚未保存，核对全年安排后统一保存归档。</NText>
                </template>
                <NEmpty v-else description="请在日历中选择日期" size="small" />
              </NSpace>
            </NCard>
            <NCard size="small" title="按星期批量设置">
              <NSpace vertical :size="12">
                <NText depth="3">仅依据正式通知设置法定日期；每周休馆安排应另行登记。</NText>
                <NForm label-placement="top" :disabled="saving" :show-feedback="false">
                  <NFormItem label="适用日期范围"><NDatePicker v-model:value="bulkRange" type="daterange" :is-date-disabled="isDateDisabled" clearable style="width:100%" /></NFormItem>
                  <NFormItem label="每周日期"><NSelect v-model:value="bulkWeekdays" multiple :options="weekdayOptions" placeholder="选择星期" /></NFormItem>
                  <NFormItem label="设置为"><NSelect v-model:value="bulkKind" :options="dayKindOptions" /></NFormItem>
                </NForm>
                <NCheckbox v-model:checked="preserveSpecial" :disabled="saving">保留已设休假及调休日期</NCheckbox>
                <NText depth="3">{{ bulkPreview.error || `本次将调整 ${bulkPreview.count} 天，保存后生效。` }}</NText>
                <NButton secondary :disabled="saving || !!bulkPreview.error || !bulkPreview.count" @click="applyWeekdays">应用批量设置</NButton>
              </NSpace>
            </NCard>
            <NCard size="small" title="正式通知依据">
              <NForm label-placement="top" :disabled="saving">
                <NFormItem label="正式通知名称" required><NInput v-model:value="form.sourceTitle" /></NFormItem>
                <NFormItem label="正式通知来源" required><NInput v-model:value="form.sourceUrl" placeholder="发布通知的官方网站链接" /></NFormItem>
                <NFormItem label="通知附件" required><HUploadInput v-model:value="form.evidenceFileIds" @upload-finish="form.evidenceFileIds = $event.id" /></NFormItem>
              </NForm>
              <NButton block type="primary" :loading="saving" :disabled="loading" @click="save">保存日历并归档通知</NButton>
            </NCard>
          </div>
        </div>
        <NCollapse><NCollapseItem title="全年特殊日期清单" name="dates">
          <NSpace vertical :size="12">
            <NText strong>休假日期（{{ form.holidays.length }} 天）</NText>
            <NSpace wrap><NTag v-for="day in form.holidays" :key="day" type="error" closable :disabled="saving" @close="removeDay('holidays', day)">{{ calendarDayKey(day) }}</NTag><NText v-if="!form.holidays.length" depth="3">未登记休假日期</NText></NSpace>
            <NText strong>调休工作日（{{ form.extraWorkdays.length }} 天）</NText>
            <NSpace wrap><NTag v-for="day in form.extraWorkdays" :key="day" type="warning" closable :disabled="saving" @close="removeDay('extraWorkdays', day)">{{ calendarDayKey(day) }}</NTag><NText v-if="!form.extraWorkdays.length" depth="3">未登记调休工作日</NText></NSpace>
          </NSpace>
        </NCollapseItem></NCollapse>
      </template>
      <NEmpty v-else :description="loading ? '正在加载日历' : '选择收藏单位和年度，加载日历或登记新日历'" />
    </NSpace>
  </NCard>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import CollectionYearCalendar from '../components/CollectionYearCalendar.vue';
import { calendarApi, type CalendarYear } from '../api/calendar';
import { orgApi } from '../api/org';
import { calendarDayKey, calendarDayKind, setCalendarDay, calendarWeekdayDates, setCalendarWeekdays, type WorkingDayKind } from '../utils/workingCalendar';
const message = useMessage();
const orgId = ref<string>(), year = ref<number | null>(new Date().getFullYear());
const orgOptions = ref<{ label: string; value: string }[]>([]);
const form = ref<CalendarYear>();
const loading = ref(false), saving = ref(false);
const month = ref(new Date().getMonth() + 1);
const panelRevision = ref(0);
const viewMode = ref<'year' | 'month'>('year');
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日'];
const weekdayOptions = weekdayLabels.map((label, index) => ({ label: `星期${label}`, value: (index + 1) % 7 }));
const dayKindOptions = [{ label: '休假日', value: 'holiday' }, { label: '调休工作日', value: 'workday' }, { label: '恢复默认（按星期）', value: 'default' }];
const bulkRange = ref<[number, number] | null>(null);
const bulkWeekdays = ref<number[]>([]);
const bulkKind = ref<WorkingDayKind>('holiday');
const preserveSpecial = ref(true);
const bulkPreview = computed(() => {
  if (!form.value || !bulkRange.value || !bulkWeekdays.value.length) return { count: 0, error: '请选择日期范围和星期。' };
  try { return { count: calendarWeekdayDates(form.value, bulkRange.value, bulkWeekdays.value, preserveSpecial.value).length, error: '' }; }
  catch (error) { return { count: 0, error: error instanceof Error ? error.message : '日期范围不正确' }; }
});
watch(form, (value, previous) => {
  if (value?.year !== previous?.year) {
    bulkRange.value = value ? [new Date(value.year, 0, 1).getTime(), new Date(value.year, 11, 31).getTime()] : null;
    bulkWeekdays.value = [];
  }
});
const monthOptions = Array.from({ length: 12 }, (_, index) => ({ label: `${index + 1} 月`, value: index + 1 }));
const selectedDate = ref<number | null>(null);
const selectedKind = ref<WorkingDayKind>('default');
const monthStart = computed(() => new Date(form.value?.year ?? year.value ?? new Date().getFullYear(), month.value - 1, 1).getTime());
let request = 0;
watch([orgId, year], () => { ++request; form.value = undefined; selectedDate.value = null; loading.value = false; }, { flush: 'sync' });
onMounted(async () => {
  const resp: any = await orgApi.page({ pageSize: 0 });
  const rows = resp.data?.list || resp.data || [];
  orgOptions.value = rows.map((row: any) => ({ label: row.name, value: row.id }));
  if (orgOptions.value.length === 1) { orgId.value = orgOptions.value[0].value; await load(); }
});
async function load() {
  if (!orgId.value || !year.value) return;
  const version = ++request, selectedOrg = orgId.value, selectedYear = year.value;
  loading.value = true;
  form.value = undefined;
  selectedDate.value = null;
  try {
    const resp: any = await calendarApi.get(selectedOrg, selectedYear);
    if (version === request) form.value = resp.data;
  } finally { if (version === request) loading.value = false; }
}
function newCalendar() {
  if (!orgId.value || !year.value || loading.value || saving.value) return;
  ++request;
  selectedDate.value = null;
  form.value = { year: year.value, holidays: [], extraWorkdays: [], sourceTitle: '', sourceUrl: '' };
}
function isDateDisabled(value: number) { return saving.value || new Date(value).getFullYear() !== form.value?.year; }
function changeMonth(info: { year: number; month: number }) {
  if (info.year === form.value?.year) month.value = info.month;
  else {
    month.value = info.year < (form.value?.year ?? info.year) ? 1 : 12;
    ++panelRevision.value;
  }
}
function selectDate(value: number) {
  if (!form.value || isDateDisabled(value)) return;
  selectedDate.value = value;
  selectedKind.value = calendarDayKind(form.value, value);
}
function dayStatus(cellYear: number, cellMonth: number, cellDate: number) {
  return dateStatus(new Date(cellYear, cellMonth - 1, cellDate).getTime());
}
function dateStatus(value: number) {
  const kind = form.value ? calendarDayKind(form.value, value) : 'default';
  if (kind === 'holiday') return { kind, label: '休假' };
  if (kind === 'workday') return { kind, label: '调休上班' };
  const weekend = [0, 6].includes(new Date(value).getDay());
  return { kind: weekend ? 'weekend' : 'default', label: weekend ? '休息' : '工作' };
}
function applySelectedDate() {
  if (!form.value || selectedDate.value === null || saving.value) return;
  form.value = setCalendarDay(form.value, selectedDate.value, selectedKind.value);
}
function applyWeekdays() {
  if (!form.value || !bulkRange.value || saving.value || bulkPreview.value.error || !bulkPreview.value.count) return;
  form.value = setCalendarWeekdays(form.value, bulkRange.value, bulkWeekdays.value, bulkKind.value, preserveSpecial.value);
  if (selectedDate.value !== null) selectedKind.value = calendarDayKind(form.value, selectedDate.value);
}
function removeDay(field: 'holidays' | 'extraWorkdays', value: number) {
  if (!form.value || saving.value) return;
  form.value[field] = form.value[field].filter((day) => calendarDayKey(day) !== calendarDayKey(value));
  if (selectedDate.value !== null) selectedKind.value = calendarDayKind(form.value, selectedDate.value);
}
async function save() {
  if (!orgId.value || !form.value || !year.value || saving.value) return;
  if (form.value.year !== year.value) { message.warning('年度已切换，请重新加载或登记新日历'); return; }
  if (!form.value.evidenceFileIds || !form.value.sourceTitle.trim() || !form.value.sourceUrl.trim()) { message.warning('请补齐正式通知名称、来源和附件'); return; }
  saving.value = true;
  try { await calendarApi.save(orgId.value, form.value); message.success('工作日历已保存，通知已永久归档'); }
  finally { saving.value = false; }
}
</script>
<style scoped>
.calendar-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.calendar-toolbar :deep(.n-form-item) { margin-bottom: 0; }
.working-calendar-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
.calendar-main { min-width: 0; }
.calendar-sidebar { display: flex; flex-direction: column; gap: 16px; }
.calendar-legend { margin-bottom: 12px; }
.calendar-day-status { display: inline-block; font-size: 12px; padding: 2px 6px; border-radius: 4px; }
.calendar-day-status.holiday { color: var(--error-color, #d03050); }
.calendar-day-status.workday { color: var(--warning-color, #b36b00); }
.calendar-day-status.weekend { color: var(--info-color, #2080f0); }
.calendar-day-status.default { color: var(--text-color-3, #767c82); }
.calendar-hint { display: block; margin-top: 12px; font-size: 12px; }
@media (max-width: 1100px) { .working-calendar-layout { grid-template-columns: minmax(0, 1fr); } .calendar-sidebar { display: grid; grid-template-columns: 1fr 1fr; } }
@media (max-width: 640px) { .calendar-sidebar { display: flex; } .calendar-main :deep(.n-calendar-cell) { padding: 6px 3px; } .calendar-day-status { padding: 2px; font-size: 11px; } }
</style>
