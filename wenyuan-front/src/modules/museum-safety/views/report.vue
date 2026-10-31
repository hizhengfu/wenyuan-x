<template>
  <SafetyPageGuard>
    <div class="report-page">
      <NGrid :cols="3" responsive="screen" :x-gap="12" :y-gap="12">
        <NGridItem :span="1">
          <NCard title="生成专项报告" :bordered="false" class="create-card">
            <NForm label-placement="top">
              <NFormItem label="场馆" required
                ><VenueSelect v-model:value="form.orgId" placeholder="请选择场馆" @update:value="handleVenueChange"
              /></NFormItem>
              <NFormItem label="报告类型" required
                ><NSelect v-model:value="form.type" :options="typeOptions"
              /></NFormItem>
              <NFormItem label="统计日期" required
                ><NDatePicker v-model:value="form.range" type="daterange" style="width: 100%"
              /></NFormItem>
              <NFormItem v-if="!form.systemId" label="建筑 / 空间"
                ><SafetyLocationTreeSelect v-model="form.locationId" :options="safetyLocations"
              /></NFormItem>
              <NFormItem v-if="!form.systemId" label="设备"
                ><NSelect v-model:value="form.deviceIds" :options="deviceOptions" multiple clearable filterable
              /></NFormItem>
              <NFormItem label="监测指标"
                ><NSelect v-model:value="form.metricCodes" :options="metricOptions" multiple clearable filterable
              /></NFormItem>
              <NAlert v-if="form.systemId" type="info" :bordered="false" class="form-hint"
                >按所选防雷整体系统生成，设备范围在创建时快照；空间和设备不能另行调整。设备范围按报告创建时当前安装关系冻结，事实按这些设备汇总，不回溯重算历史位置。报告按自然日读取统计数据。</NAlert
              >
              <NAlert v-else type="info" :bordered="false" class="form-hint"
                >设备范围按报告创建时当前安装关系冻结，事实按这些设备汇总，不回溯重算历史位置。报告按自然日读取统计数据；实时值和细粒度历史仍从监测事实读取。未选择设备或指标时，使用当前空间下该报告类型的全部安全数据。</NAlert
              >
              <NButton v-if="canReport" type="primary" block :loading="submitting" @click="createReport"
                >生成报告</NButton
              >
              <NAlert v-else type="warning" :bordered="false">当前账号只有查看权限，不能生成报告。</NAlert>
            </NForm>
          </NCard>
        </NGridItem>
        <NGridItem :span="2">
          <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
          <NAlert v-if="catalogError" type="warning" closable class="state-alert" @close="catalogError = ''">{{
            catalogError
          }}</NAlert>
          <HPageDataTable
            ref="tableRef"
            title="报告列表"
            class="list-card safety-page-table"
            :columns="columns"
            :fetch-data="fetchReports"
            :params="reportQuery"
            :scroll-x="1180"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
            @update:data="handleRows"
          >
            <template #queryForm>
              <NFormItem label="所属场馆"
                ><VenueSelect
                  v-model:value="form.orgId"
                  clearable
                  placeholder="全部场馆"
                  style="width: 240px"
                  @update:value="handleVenueChange"
              /></NFormItem>
              <NFormItem label="报告类型"
                ><NSelect
                  v-model:value="typeFilter"
                  :options="typeOptions"
                  clearable
                  placeholder="全部类型"
                  style="width: 180px"
              /></NFormItem>
            </template>
          </HPageDataTable>
        </NGridItem>
      </NGrid>

      <NModal
        v-model:show="summaryVisible"
        preset="card"
        title="报告摘要"
        style="width: 680px; max-width: calc(100vw - 32px)"
      >
        <NDescriptions v-if="selected" bordered :column="2">
          <NDescriptionsItem label="场馆">{{ venueName(selected.orgId) }}</NDescriptionsItem>
          <NDescriptionsItem label="类型">{{ typeLabel(selected.type) }}</NDescriptionsItem>
          <NDescriptionsItem label="状态">{{ statusLabel(selected.status) }}</NDescriptionsItem>
          <NDescriptionsItem label="生成时间">{{ formatDateTime(selected.createdAt) }}</NDescriptionsItem>
          <NDescriptionsItem label="数据截止">{{
            selected.dataCutoffAt ? formatDateTime(selected.dataCutoffAt) : '--'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="版本">{{ selected.generatorVersion }}</NDescriptionsItem>
          <NDescriptionsItem label="文件引用">{{ selected.fileReference || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="时间范围"
            >{{ formatDateTime(selected.from) }} 至 {{ formatDateTime(selected.to) }}</NDescriptionsItem
          >
          <NDescriptionsItem label="摘要" :span="2">
            <pre class="summary-json">{{ formatSummary(selected.summary) }}</pre>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="selected.errorMessage" label="失败原因" :span="2">{{
            selected.errorMessage
          }}</NDescriptionsItem>
        </NDescriptions>
      </NModal>
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NAlert,
  NButton,
  NCard,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NGrid,
  NGridItem,
  NModal,
  NSelect,
  NSpace,
  NTag,
  type DataTableColumns,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import { ingestApi } from '../../museum-device/api/ingest';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import { loadVenueCatalog } from '../../museum-device/utils/venueCatalog';
import type { MuseumVenueVo, RecordFilterOptionsVo, StandardMetricDefinition } from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type { SafetyReportCommand, SafetyReportType, SafetyReportVo } from '../typings';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';
import SafetyLocationTreeSelect from '../components/SafetyLocationTreeSelect.vue';
import { filterSafetyLocations, safetyLocationSubtreeIds } from '../utils/location';

const message = useMessage();
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const canSelect = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_SELECT'));
const canReport = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_REPORT'));
const submitting = ref(false);
const error = ref('');
const catalogError = ref('');
const rows = ref<SafetyReportVo[]>([]);
const tableRef = ref<any>(null);
const typeFilter = ref<SafetyReportType | null>(null);
const catalogs = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const metrics = ref<StandardMetricDefinition[]>([]);
const venues = ref<MuseumVenueVo[]>([]);
const safetyLocations = computed(() => filterSafetyLocations(catalogs.value.locations));
const summaryVisible = ref(false);
const selected = ref<SafetyReportVo | null>(null);
let refreshTimer: number | null = null;
let pageActive = true;
let firstActivation = true;
const DAY_MILLIS = 24 * 60 * 60 * 1000;
const today = new Date();
today.setHours(0, 0, 0, 0);
const typeOptions: Array<{ label: string; value: SafetyReportType }> = [
  { label: '结构监测报告', value: 'STRUCTURE' },
  { label: '防雷报告', value: 'LIGHTNING' },
  { label: '消防报告', value: 'FIRE' },
  { label: '安防报告', value: 'SECURITY' },
  { label: '用电报告', value: 'ELECTRICITY' },
  { label: '综合安全报告', value: 'COMPREHENSIVE' },
];
function reportTypeFromRoute(): SafetyReportType | null {
  const value = route.query.type as SafetyReportType | undefined;
  return value && typeOptions.some((item) => item.value === value) ? value : null;
}
const initialReportType = reportTypeFromRoute() || 'COMPREHENSIVE';
const form = reactive({
  orgId: ((route.query.orgId as string) || null) as string | null,
  systemId: initialReportType === 'LIGHTNING' ? (((route.query.systemId as string) || null) as string | null) : null,
  type: initialReportType,
  range: [today.getTime() - 29 * DAY_MILLIS, today.getTime()] as [number, number] | null,
  locationId: null as string | null,
  deviceIds: [] as string[],
  metricCodes: [] as string[],
});
const reportLocationIds = computed(() => {
  if (!form.locationId) return new Set(safetyLocations.value.map((item) => item.id));
  return safetyLocationSubtreeIds(form.locationId, safetyLocations.value);
});
const deviceOptions = computed(() =>
  catalogs.value.devices
    .filter((item) => !form.locationId || (item.locationId && reportLocationIds.value.has(item.locationId)))
    .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id })),
);
const reportCategories: Record<SafetyReportType, string[]> = {
  STRUCTURE: ['STRUCTURE'],
  LIGHTNING: ['LIGHTNING_PROTECTION'],
  FIRE: ['FIRE'],
  SECURITY: ['SECURITY'],
  ELECTRICITY: ['ELECTRICITY'],
  COMPREHENSIVE: ['STRUCTURE', 'LIGHTNING_PROTECTION', 'FIRE', 'SECURITY', 'ELECTRICITY', 'BUILDING_ENVIRONMENT'],
};
const metricOptions = computed(() =>
  metrics.value
    .filter((item) => reportCategories[form.type].includes(item.category))
    .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.code })),
);
const reportQuery = computed(() => ({
  orgId: form.orgId || undefined,
  type: typeFilter.value || undefined,
}));

watch(
  () => form.type,
  (value) => {
    form.metricCodes = [];
    if (value !== 'LIGHTNING' && form.systemId) {
      form.systemId = null;
      void router.replace({ query: { ...route.query, type: value, systemId: undefined } });
    }
  },
);
watch(
  () => form.systemId,
  (value) => {
    if (!value) return;
    form.locationId = null;
    form.deviceIds = [];
  },
);
watch(
  () => form.locationId,
  () => {
    const visible = new Set(deviceOptions.value.map((item) => item.value));
    form.deviceIds = form.deviceIds.filter((id) => visible.has(id));
  },
);

const columns: DataTableColumns<SafetyReportVo> = [
  { title: '场馆', key: 'orgId', width: 150, render: (row) => venueName(row.orgId) },
  { title: '报告类型', key: 'type', width: 150, render: (row) => typeLabel(row.type) },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { type: row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'error' : 'warning', bordered: false },
        { default: () => statusLabel(row.status) },
      ),
  },
  {
    title: '报告范围',
    key: 'range',
    minWidth: 300,
    render: (row) => `${formatDateTime(row.from)} 至 ${formatDateTime(row.to)}`,
  },
  { title: '生成时间', key: 'createdAt', width: 180, render: (row) => formatDateTime(row.createdAt) },
  {
    title: '数据截止',
    key: 'dataCutoffAt',
    width: 180,
    render: (row) => (row.dataCutoffAt ? formatDateTime(row.dataCutoffAt) : '--'),
  },
  { title: '版本', key: 'generatorVersion', width: 90 },
  {
    title: '失败原因',
    key: 'errorMessage',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (row) => row.errorMessage || '--',
  },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 190,
    render: (row) =>
      h(NSpace, null, {
        default: () => [
          h(NButton, { text: true, type: 'primary', onClick: () => showSummary(row) }, { default: () => '摘要' }),
          row.status === 'SUCCESS' && canReport.value
            ? h(NButton, { text: true, type: 'primary', onClick: () => download(row) }, { default: () => '下载' })
            : null,
          (row.status === 'FAILED' || row.status === 'SUCCESS') && canReport.value
            ? h(
                NButton,
                { text: true, type: row.status === 'FAILED' ? 'warning' : 'default', onClick: () => regenerate(row) },
                { default: () => '重新生成' },
              )
            : null,
        ],
      }),
  },
];

async function fetchReports(params: Record<string, unknown>) {
  if (!canSelect.value) return;
  error.value = '';
  try {
    return await safetyApi.reports(params);
  } catch (cause: any) {
    error.value = cause?.message || '报告列表加载失败。';
    throw cause;
  }
}
function handleRows(value: SafetyReportVo[]) {
  rows.value = value;
  if (selected.value) selected.value = value.find((row) => row.id === selected.value?.id) || selected.value;
  scheduleRefresh();
}
async function createReport() {
  if (!form.orgId) {
    error.value = '请选择报告所属场馆。';
    return;
  }
  if (!form.range) {
    error.value = '请选择报告时间范围。';
    return;
  }
  submitting.value = true;
  error.value = '';
  const exclusiveEnd = new Date(form.range[1]);
  exclusiveEnd.setDate(exclusiveEnd.getDate() + 1);
  const command: SafetyReportCommand = {
    orgId: form.orgId,
    type: form.type,
    from: form.range[0],
    to: exclusiveEnd.getTime(),
    metricCodes: form.metricCodes,
    ...(form.systemId ? { systemId: form.systemId } : { locationId: form.locationId, deviceIds: form.deviceIds }),
  };
  try {
    await safetyApi.createReport(command);
    message.success('报告生成任务已创建');
    tableRef.value?.handleQuery?.();
  } catch (cause: any) {
    error.value = cause?.message || '报告生成失败，已保留当前生成条件。';
  } finally {
    submitting.value = false;
  }
}
async function regenerate(row: SafetyReportVo) {
  try {
    await safetyApi.regenerateReport(row.id);
    message.success('报告已重新生成');
    tableRef.value?.handleRefresh?.();
  } catch (cause: any) {
    error.value = cause?.message || '重新生成失败。';
  }
}
async function download(row: SafetyReportVo) {
  try {
    const response: any = await safetyApi.downloadReport(row.id);
    const blob =
      response instanceof Blob
        ? response
        : response?.data instanceof Blob
          ? response.data
          : new Blob([response], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `场馆安全-${row.type.toLowerCase()}-${row.id}.html`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (cause: any) {
    error.value = cause?.message || '报告下载失败。';
  }
}
function showSummary(row: SafetyReportVo) {
  selected.value = row;
  summaryVisible.value = true;
}
async function loadCatalogs() {
  const [catalogResponse, metricResponse] = await Promise.all([
    form.orgId ? ingestApi.recordFilterOptions({ orgId: form.orgId }) : Promise.resolve(null),
    ingestApi.standardMetrics(true),
  ]);
  catalogs.value = catalogResponse
    ? unwrapResponse<RecordFilterOptionsVo>(catalogResponse)
    : { devices: [], locations: [] };
  metrics.value = unwrapResponse<StandardMetricDefinition[]>(metricResponse);
}
async function handleVenueChange() {
  form.systemId = null;
  form.locationId = null;
  form.deviceIds = [];
  form.metricCodes = [];
  await router.replace({ query: { ...route.query, orgId: form.orgId || undefined, systemId: undefined } });
  try {
    await loadCatalogs();
    catalogError.value = '';
  } catch {
    catalogs.value = { devices: [], locations: [] };
    catalogError.value = '当前场馆报告筛选目录加载失败。';
  }
  tableRef.value?.handleQuery?.();
}
function typeLabel(value: string) {
  return typeOptions.find((item) => item.value === value)?.label || value;
}
function venueName(value: string) {
  return (
    venues.value.find((item) => item.id === value)?.shortName ||
    venues.value.find((item) => item.id === value)?.name ||
    value
  );
}
function statusLabel(value: string) {
  return (
    ({ GENERATING: '生成中', PROCESSING: '处理中', SUCCESS: '成功', FAILED: '失败' } as Record<string, string>)[
      value
    ] || value
  );
}
function formatSummary(value: Record<string, unknown>) {
  return JSON.stringify(value || {}, null, 2);
}
function scheduleRefresh() {
  if (refreshTimer != null) window.clearTimeout(refreshTimer);
  refreshTimer =
    canSelect.value &&
    pageActive &&
    rows.value.some((row) => row.status === 'GENERATING' || row.status === 'PROCESSING')
      ? window.setTimeout(() => {
          tableRef.value?.handleRefresh?.();
        }, 3000)
      : null;
}
onMounted(async () => {
  if (!canSelect.value) return;
  try {
    venues.value = await loadVenueCatalog();
    if (venues.value.length === 1) form.orgId = venues.value[0].id;
    await loadCatalogs();
  } catch {
    catalogError.value = '报告筛选目录加载失败，仍可生成全范围报告。';
  }
  tableRef.value?.handleQuery?.();
});
onActivated(() => {
  pageActive = true;
  if (firstActivation) {
    firstActivation = false;
    return;
  }
  if (!canSelect.value) return;
  form.orgId = ((route.query.orgId as string) || null) as string | null;
  const requestedType = reportTypeFromRoute();
  if (requestedType) form.type = requestedType;
  form.systemId = form.type === 'LIGHTNING' ? (((route.query.systemId as string) || null) as string | null) : null;
  void loadCatalogs()
    .catch(() => {
      catalogs.value = { devices: [], locations: [] };
      catalogError.value = '报告筛选目录加载失败，仍可查看报告列表。';
    })
    .then(() => tableRef.value?.handleRefresh?.());
});
onDeactivated(() => {
  pageActive = false;
  if (refreshTimer != null) window.clearTimeout(refreshTimer);
  refreshTimer = null;
});
onBeforeUnmount(() => {
  pageActive = false;
  if (refreshTimer != null) window.clearTimeout(refreshTimer);
});
</script>

<style scoped>
.report-page {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 12px;
  overflow: auto;
  box-sizing: border-box;
}
.create-card,
.list-card {
  min-height: calc(100vh - 120px);
}
.form-hint,
.state-alert {
  margin-bottom: 14px;
}
.empty {
  padding: 40px 0;
}
.summary-json {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
