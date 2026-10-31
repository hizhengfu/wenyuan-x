<template>
  <NCard title="开放与休馆日历">
    <NSpace vertical :size="16">
      <NAlert type="info">用于本馆开放与休馆安排。临时开放、闭馆优先于每周规则；未设休馆的日期按开放显示。此日历不参与法定业务期限计算。</NAlert>
      <div class="opening-toolbar">
        <NForm inline label-placement="left" :show-feedback="false">
          <NFormItem label="收藏单位"><NSelect v-model:value="orgId" :options="orgOptions" filterable :disabled="saving" style="width:240px" /></NFormItem>
          <NFormItem label="年度"><NInputNumber v-model:value="year" :min="1900" :max="2200" :precision="0" :disabled="saving" style="width:130px" /></NFormItem>
        </NForm>
        <NSpace><NButton :disabled="!orgId || !year || saving" :loading="loading" @click="load">加载日历</NButton><NButton v-if="canManage" type="primary" :disabled="!form || loading" :loading="saving" @click="save">保存安排</NButton></NSpace>
      </div>
      <template v-if="form">
        <NSpace align="center" wrap><NTag :type="form.revision ? 'info' : 'warning'">{{ form.revision ? `已保存版本 ${form.revision}` : '尚未登记，当前为草稿' }}</NTag><NText v-if="dirty" type="warning">有未保存修改</NText><NText depth="3">修改后点击“保存安排”生效。</NText></NSpace>
        <div class="opening-layout">
          <NCard size="small" title="全年开放安排" class="opening-main">
            <template #header-extra><NRadioGroup v-model:value="viewMode" size="small"><NRadioButton value="year">全年</NRadioButton><NRadioButton value="month">单月</NRadioButton></NRadioGroup></template>
            <NSpace class="opening-legend" wrap><NTag :bordered="false">开放</NTag><NTag type="error" :bordered="false">休馆</NTag><NTag type="warning" :bordered="false">临时开放</NTag><NText depth="3">按规则预览</NText></NSpace>
            <CollectionYearCalendar v-if="viewMode === 'year'" :year="form.year" :selected-date="selectedDate" :disabled="saving" :status="dateStatus" @select="selectDate" @month="month = $event; viewMode = 'month'" />
            <template v-else>
              <NSelect v-model:value="month" :options="monthOptions" aria-label="查看月份" style="width:110px;margin-bottom:12px" />
              <NCalendar :key="`${form.year}-${month}-${panelRevision}`" :value="selectedDate" :default-value="monthStart" :is-date-disabled="isDateDisabled" @update:value="selectDate" @panel-change="changeMonth">
                <template #default="{ year: cellYear, month: cellMonth, date: cellDate }"><span :class="['opening-date-status', dateStatus(new Date(cellYear, cellMonth - 1, cellDate).getTime()).kind]">{{ dateStatus(new Date(cellYear, cellMonth - 1, cellDate).getTime()).label }}</span></template>
              </NCalendar>
            </template>
          </NCard>
          <div class="opening-sidebar">
            <NCard size="small" title="每周休馆规则">
              <NSpace vertical :size="12">
                <NSpace v-if="canManage" wrap><NButton secondary :disabled="saving" @click="addMonday">全年每周一休馆</NButton><NButton secondary :disabled="saving" @click="addRule">添加规则</NButton></NSpace>
                <NEmpty v-if="!form.weeklyClosures.length" description="尚未设置固定休馆规则" size="small" />
                <div v-for="(rule, index) in form.weeklyClosures" :key="index" class="opening-rule">
                  <NForm label-placement="top" :show-feedback="false" :disabled="saving || !canManage">
                    <NFormItem label="适用期间"><NDatePicker :value="[rule.startOn, rule.endOn]" type="daterange" :is-date-disabled="isDateDisabled" style="width:100%" @update:value="updateRange(index, $event)" /></NFormItem>
                    <NFormItem label="每周休馆日"><NSelect v-model:value="rule.weekdays" multiple :options="weekdayOptions" /></NFormItem>
                    <NFormItem label="说明"><NInput v-model:value="rule.remark" :maxlength="1000" placeholder="如常规休馆、冬季安排" /></NFormItem>
                  </NForm>
                  <NButton v-if="canManage" size="small" text type="error" :disabled="saving" @click="removeRule(index)">移除此规则</NButton>
                </div>
              </NSpace>
            </NCard>
            <NCard size="small" title="临时开放或闭馆">
              <NSpace vertical :size="12">
                <template v-if="selectedDate !== null">
                  <NText strong>{{ calendarDayKey(selectedDate) }} · {{ dateStatus(selectedDate).label }}</NText>
                  <NRadioGroup v-model:value="exceptionKind" :disabled="saving || !canManage"><NSpace vertical><NRadio value="open">临时开放</NRadio><NRadio value="closed">临时闭馆</NRadio><NRadio value="default">恢复每周规则</NRadio></NSpace></NRadioGroup>
                  <NInput v-if="exceptionKind !== 'default'" v-model:value="exceptionReason" placeholder="临时安排原因（必填）" :maxlength="1000" :disabled="saving || !canManage" />
                  <NButton v-if="canManage" secondary type="primary" :disabled="saving" @click="applyException">应用到此日期</NButton>
                </template>
                <NEmpty v-else description="请在日历中选择日期" size="small" />
              </NSpace>
            </NCard>
          </div>
        </div>
        <NFormItem label="全年安排说明"><NInput v-model:value="form.remark" :maxlength="2000" type="textarea" :rows="2" :disabled="saving || !canManage" /></NFormItem>
        <NCollapse><NCollapseItem :title="`临时安排清单（${form.exceptions.length} 天）`" name="exceptions"><NSpace vertical><div v-for="item in form.exceptions" :key="item.date"><NSpace align="center"><NTag :type="item.open ? 'warning' : 'error'">{{ calendarDayKey(item.date) }} {{ item.open ? '临时开放' : '临时闭馆' }}</NTag><NText>{{ item.reason }}</NText><NButton v-if="canManage" size="small" text :disabled="saving" @click="clearException(item.date)">恢复每周规则</NButton></NSpace></div><NEmpty v-if="!form.exceptions.length" size="small" description="暂无临时安排" /></NSpace></NCollapseItem></NCollapse>
      </template>
      <NEmpty v-else :description="loading ? '正在加载日历' : '选择收藏单位和年度后加载日历'" />
    </NSpace>
  </NCard>
</template>
<script setup lang="ts">
import { computed, onActivated, onDeactivated, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import { orgApi } from '../api/org';
import { openingCalendarApi, type OpeningCalendarYear } from '../api/openingCalendar';
import CollectionYearCalendar from '../components/CollectionYearCalendar.vue';
import { calendarDayKey } from '../utils/workingCalendar';
import { openingDayStatus, setOpeningException } from '../utils/openingCalendar';
const message = useMessage();
const auth = useAuthStore();
const canManage = computed(() => auth.hasPermission('ADMIN,COLLECTION_ADMIN'));
const orgId = ref<string>(), year = ref<number | null>(new Date().getFullYear());
const orgOptions = ref<{ label: string; value: string }[]>([]);
const form = ref<OpeningCalendarYear>();
const loading = ref(false), saving = ref(false), savedContent = ref('');
const dirty = computed(() => !!form.value && JSON.stringify(form.value) !== savedContent.value);
const viewMode = ref<'year' | 'month'>('year'), month = ref(new Date().getMonth() + 1), panelRevision = ref(0);
const monthOptions = Array.from({ length: 12 }, (_, i) => ({ label: `${i + 1} 月`, value: i + 1 }));
const weekdayOptions = ['一', '二', '三', '四', '五', '六', '日'].map((label, index) => ({ label: `星期${label}`, value: index + 1 }));
const monthStart = computed(() => new Date(form.value?.year ?? year.value ?? 2026, month.value - 1, 1).getTime());
const selectedDate = ref<number | null>(null), exceptionKind = ref<'open' | 'closed' | 'default'>('default'), exceptionReason = ref('');
let request = 0, active = true, wasInactive = false;
watch([orgId, year], () => { ++request; form.value = undefined; selectedDate.value = null; loading.value = false; }, { flush: 'sync' });
onBeforeUnmount(() => { active = false; ++request; });
onDeactivated(() => { active = false; wasInactive = true; ++request; form.value = undefined; loading.value = false; });
onActivated(() => { active = true; if (wasInactive) { wasInactive = false; if (!orgOptions.value.length) void loadOrgs(); else void load(); } });
watch(() => auth.userInfo?.id, () => {
  ++request; form.value = undefined; selectedDate.value = null; orgId.value = undefined; orgOptions.value = []; loading.value = false;
  if (active) void loadOrgs();
}, { flush: 'sync' });
onMounted(loadOrgs);
async function loadOrgs() {
  const version = ++request;
  const resp: any = await orgApi.page({ pageSize: 0 });
  if (!active || version !== request) return;
  orgOptions.value = (resp.data?.list || resp.data || []).map((row: any) => ({ label: row.name, value: row.id }));
  if (orgOptions.value.length === 1) { orgId.value = orgOptions.value[0].value; await load(); }
}
async function load() {
  if (!active || !orgId.value || !year.value || saving.value) return;
  const version = ++request, selectedOrg = orgId.value, selectedYear = year.value;
  form.value = undefined; selectedDate.value = null; loading.value = true;
  try {
    const resp: any = await openingCalendarApi.get(selectedOrg, selectedYear);
    if (!active || version !== request) return;
    const value: OpeningCalendarYear = resp.data || { year: selectedYear, revision: 0, weeklyClosures: [], exceptions: [] };
    if (value.year !== selectedYear) throw new Error('开放日历年度不一致');
    form.value = value; savedContent.value = JSON.stringify(value);
  } catch (error) { if (active && version === request) message.error(error instanceof Error ? error.message : '开放日历加载失败'); }
  finally { if (version === request) loading.value = false; }
}
function dateStatus(date: number) { return form.value ? openingDayStatus(form.value, date) : { kind: 'default', label: '未登记' }; }
function isDateDisabled(date: number) { return saving.value || new Date(date).getFullYear() !== form.value?.year; }
function changeMonth(info: { year: number; month: number }) {
  if (info.year === form.value?.year) month.value = info.month;
  else { month.value = info.year < (form.value?.year ?? info.year) ? 1 : 12; ++panelRevision.value; }
}
function selectDate(date: number) {
  if (!form.value || isDateDisabled(date)) return;
  selectedDate.value = date;
  const value = form.value.exceptions.find((item) => calendarDayKey(item.date) === calendarDayKey(date));
  exceptionKind.value = value ? (value.open ? 'open' : 'closed') : 'default'; exceptionReason.value = value?.reason || '';
}
function addRule() {
  if (!form.value || !canManage.value || saving.value) return;
  form.value.weeklyClosures.push({ startOn: new Date(form.value.year, 0, 1).getTime(), endOn: new Date(form.value.year, 11, 31).getTime(), weekdays: [], remark: '' });
}
function addMonday() {
  if (!form.value || !canManage.value || saving.value) return;
  const startOn = new Date(form.value.year, 0, 1).getTime(), endOn = new Date(form.value.year, 11, 31).getTime();
  if (form.value.weeklyClosures.some((rule) => rule.startOn === startOn && rule.endOn === endOn && rule.weekdays.includes(1))) { message.info('已设置全年每周一休馆'); return; }
  form.value.weeklyClosures.push({ startOn, endOn, weekdays: [1], remark: '每周一常规休馆' });
}
function updateRange(index: number, value: [number, number] | null) {
  if (!form.value || !value || saving.value || !canManage.value) return;
  Object.assign(form.value.weeklyClosures[index], { startOn: value[0], endOn: value[1] });
}
function removeRule(index: number) { if (form.value && !saving.value && canManage.value) form.value.weeklyClosures.splice(index, 1); }
function applyException() {
  if (!form.value || selectedDate.value === null || saving.value || !canManage.value) return;
  try { form.value = setOpeningException(form.value, selectedDate.value, exceptionKind.value === 'default' ? null : { open: exceptionKind.value === 'open', reason: exceptionReason.value }); }
  catch (error) { message.warning(error instanceof Error ? error.message : '请核对临时安排'); }
}
function clearException(date: number) {
  if (!form.value || saving.value || !canManage.value) return;
  form.value = setOpeningException(form.value, date, null);
  if (selectedDate.value !== null) selectDate(selectedDate.value);
}
async function save() {
  if (!form.value || !orgId.value || saving.value || !canManage.value) return;
  if (form.value.weeklyClosures.some((rule) => !rule.weekdays.length || new Date(rule.startOn).getFullYear() !== form.value!.year || new Date(rule.endOn).getFullYear() !== form.value!.year || rule.startOn > rule.endOn)) { message.warning('请补齐每周规则的本年度日期范围和休馆星期'); return; }
  saving.value = true;
  const version = request;
  try {
    const resp: any = await openingCalendarApi.save(orgId.value, JSON.parse(JSON.stringify(form.value)));
    if (!active || version !== request) return;
    form.value = resp.data; savedContent.value = JSON.stringify(form.value); message.success('开放与休馆安排已保存');
  } finally { saving.value = false; }
}
</script>
<style scoped>
.opening-toolbar { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:12px; }
.opening-toolbar :deep(.n-form-item) { margin-bottom:0; }
.opening-layout { display:grid; grid-template-columns:minmax(0,1fr) 340px; gap:16px; align-items:start; }
.opening-main { min-width:0; }
.opening-sidebar { display:flex; flex-direction:column; gap:16px; }
.opening-legend { margin-bottom:12px; }
.opening-rule { border:1px solid var(--border-color,#e5e7eb); border-radius:6px; padding:12px; }
.opening-rule :deep(.n-form-item) { margin-bottom:12px; }
.opening-date-status { font-size:12px; }
.opening-date-status.holiday { color:var(--error-color,#d03050); }
.opening-date-status.workday { color:var(--warning-color,#b36b00); }
@media(max-width:1100px) { .opening-layout { grid-template-columns:minmax(0,1fr); } }
</style>
