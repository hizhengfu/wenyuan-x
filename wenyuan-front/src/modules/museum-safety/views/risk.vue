<template>
  <SafetyPageGuard>
    <div class="risk-page">
      <NTabs v-model:value="filters.status" type="segment" class="status-tabs" @update:value="search">
        <NTabPane v-for="item in statusOptions" :key="item.value" :name="item.value" :tab="item.label" />
      </NTabs>
      <NAlert v-if="filters.systemId" type="info" :bordered="false" class="state-alert">
        当前仅查看防雷系统“{{ filters.systemName || filters.systemId }}”的风险。
        <NButton text type="primary" @click="clearSystemFilter">清除系统筛选</NButton>
      </NAlert>
      <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
      <NAlert v-if="catalogError" type="warning" closable class="state-alert" @close="catalogError = ''">{{
        catalogError
      }}</NAlert>
      <HPageDataTable
        ref="tableRef"
        title="风险处置"
        class="safety-page-table"
        :columns="columns"
        :fetch-data="fetchRisks"
        :params="tableQuery"
        :scroll-x="1450"
        row-key-field="id"
        :is-add="false"
        :is-edit="false"
        :is-copy="false"
        :is-delete="false"
        :is-export="false"
        :show-operate="false"
        clear-selection-on-fetch
        @update-checked-row-keys="handleCheckedRows"
      >
        <template #queryForm>
          <NFormItem label="所属场馆"
            ><VenueSelect
              v-model:value="filters.orgId"
              clearable
              placeholder="全部场馆"
              style="width: 240px"
              @update:value="handleVenueChange"
          /></NFormItem>
          <NFormItem label="安全领域"
            ><NSelect
              v-model:value="filters.domain"
              :options="domainOptions"
              clearable
              placeholder="全部领域"
              style="width: 150px"
          /></NFormItem>
          <NFormItem label="风险等级"
            ><NSelect
              v-model:value="filters.level"
              :options="levelOptions"
              clearable
              placeholder="全部等级"
              style="width: 140px"
          /></NFormItem>
          <NFormItem label="空间位置"
            ><SafetyLocationTreeSelect v-model="filters.locationId" :options="safetyLocations"
          /></NFormItem>
          <NFormItem label="设备"
            ><NSelect
              v-model:value="filters.deviceId"
              :options="deviceOptions"
              clearable
              filterable
              placeholder="全部设备"
              style="width: 240px"
          /></NFormItem>
          <NFormItem label="责任人"
            ><UserSelect v-model:value="filters.assigneeId" clearable placeholder="全部责任人" style="width: 220px"
          /></NFormItem>
          <NFormItem label="发生时间"
            ><NDatePicker v-model:value="filters.range" type="datetimerange" clearable style="width: 350px"
          /></NFormItem>
        </template>
        <template #toolbar-extra>
          <NSpace>
            <UserSelect
              v-if="canManage"
              v-model:value="batchAssignee"
              clearable
              placeholder="选择批量指派责任人"
              style="width: 220px"
            />
            <NButton
              v-if="canManage"
              :disabled="!selectedIds.length || !batchAssignee"
              :loading="submitting"
              @click="batchAssign"
              >批量指派</NButton
            >
            <NButton @click="exportCurrent">导出当前筛选结果</NButton>
            <NButton @click="reset">重置筛选</NButton>
          </NSpace>
        </template>
      </HPageDataTable>

      <RiskEventHandling v-model:show="detailVisible" :id="detailId" @saved="refreshList" />
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, onActivated, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NAlert,
  NButton,
  NDatePicker,
  NFormItem,
  NSelect,
  NSpace,
  NTabPane,
  NTabs,
  NTag,
  type DataTableColumns,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { ingestApi } from '../../museum-device/api/ingest';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import { loadVenueCatalog } from '../../museum-device/utils/venueCatalog';
import type { MuseumVenueVo, RecordFilterOptionsVo } from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type { SafetyRiskStatus, SafetyRiskVo } from '../typings';
import RiskEventHandling from '../components/RiskEventHandling.vue';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';
import SafetyLocationTreeSelect from '../components/SafetyLocationTreeSelect.vue';
import { filterSafetyLocations, safetyLocationSubtreeIds } from '../utils/location';

const message = useMessage();
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const canSelect = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_SELECT'));
const canManage = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_MANAGE'));
const submitting = ref(false);
const error = ref('');
const catalogError = ref('');
const tableRef = ref<any>(null);
const selectedIds = ref<string[]>([]);
const batchAssignee = ref('');
const detailVisible = ref(false);
const detailId = ref('');
let firstActivation = true;
const catalogs = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const venues = ref<MuseumVenueVo[]>([]);
const safetyLocations = computed(() => filterSafetyLocations(catalogs.value.locations));
const filters = reactive({
  orgId: (route.query.orgId as string) || null,
  systemId: (route.query.systemId as string) || null,
  systemName: (route.query.systemName as string) || null,
  domain: (route.query.domain as string) || null,
  level: (route.query.level as string) || null,
  status: ((route.query.status as SafetyRiskStatus | 'ACTIVE') ||
    (route.query.activeOnly === 'true' ? 'ACTIVE' : 'PENDING_CONFIRMATION')) as SafetyRiskStatus | 'ACTIVE',
  majorOnly: route.query.majorOnly === 'true',
  locationId: (route.query.locationId as string) || null,
  deviceId: (route.query.deviceId as string) || null,
  assigneeId: (route.query.assigneeId as string) || null,
  range: route.query.from
    ? ([Number(route.query.from), route.query.to ? Number(route.query.to) : Date.now()] as [number, number])
    : (null as [number, number] | null),
});
const domainOptions = [
  { label: '建筑结构', value: 'BUILDING' },
  { label: '防雷', value: 'LIGHTNING' },
  { label: '消防', value: 'FIRE' },
  { label: '安防', value: 'SECURITY' },
  { label: '用电', value: 'ELECTRICITY' },
  { label: '建筑环境', value: 'BUILDING_ENVIRONMENT' },
];
const levelOptions = [
  { label: '提示', value: 'NOTICE' },
  { label: '警告', value: 'WARNING' },
  { label: '重大', value: 'MAJOR' },
  { label: '严重', value: 'CRITICAL' },
];
const statusOptions = [
  { label: '全部活动', value: 'ACTIVE' },
  { label: '待确认', value: 'PENDING_CONFIRMATION' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已恢复待确认', value: 'RECOVERED_PENDING_CONFIRMATION' },
  { label: '已关闭', value: 'CLOSED' },
];
const selectedLocationIds = computed(() =>
  filters.locationId ? safetyLocationSubtreeIds(filters.locationId, safetyLocations.value) : new Set<string>(),
);
const deviceOptions = computed(() =>
  catalogs.value.devices
    .filter((item) => !filters.locationId || (item.locationId && selectedLocationIds.value.has(item.locationId)))
    .map((item) => ({
      label: `${item.name}（${item.code}）${item.locationPath ? ` · ${item.locationPath}` : ''}`,
      value: item.id,
    })),
);
const tableQuery = computed(() => queryParams());
const columns: DataTableColumns<SafetyRiskVo> = [
  { type: 'selection', disabled: (row) => ['CLOSED', 'PENDING_CONFIRMATION'].includes(row.status) },
  { title: '场馆', key: 'orgId', width: 150, render: (row) => venueName(row.orgId) },
  { title: '领域', key: 'domain', width: 100, render: (row) => domainLabel(row.domain) },
  {
    title: '等级',
    key: 'level',
    width: 90,
    render: (row) =>
      h(
        NTag,
        {
          type:
            row.level === 'CRITICAL' || row.level === 'MAJOR'
              ? 'error'
              : row.level === 'WARNING'
                ? 'warning'
                : 'default',
          bordered: false,
        },
        { default: () => levelLabel(row.level) },
      ),
  },
  { title: '风险', key: 'title', minWidth: 240 },
  { title: '设备', key: 'deviceName', minWidth: 170, render: (row) => row.deviceName || row.deviceId },
  {
    title: '触发位置',
    key: 'locationSnapshotPath',
    minWidth: 220,
    render: (row) => row.locationSnapshotPath || row.locationSnapshotId || '--',
  },
  {
    title: '当前位置',
    key: 'currentLocationPath',
    minWidth: 220,
    render: (row) => row.currentLocationPath || row.currentLocationId || '--',
  },
  { title: '责任人', key: 'assigneeId', width: 140, render: (row) => row.assigneeId || '未指派' },
  { title: '发生次数', key: 'occurrenceCount', width: 90 },
  { title: '最近发生', key: 'lastOccurredAt', width: 180, render: (row) => formatDateTime(row.lastOccurredAt) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 130,
    render: (row) =>
      h(
        NButton,
        { text: true, type: 'primary', onClick: () => openDetail(row.id) },
        { default: () => (canManage.value ? '详情 / 处置' : '详情') },
      ),
  },
];

function queryParams(all = false) {
  return {
    pageNum: all ? 1 : undefined,
    pageSize: all ? 0 : undefined,
    orgId: filters.orgId || undefined,
    systemId: filters.systemId || undefined,
    domain: filters.domain || undefined,
    level: filters.level || undefined,
    status: filters.status === 'ACTIVE' ? undefined : filters.status || undefined,
    activeOnly: filters.status === 'ACTIVE' || undefined,
    majorOnly: filters.majorOnly || undefined,
    locationId: filters.locationId || undefined,
    deviceId: filters.deviceId || undefined,
    assigneeId: filters.assigneeId || undefined,
    from: filters.range?.[0],
    to: filters.range?.[1],
  };
}
function applyRouteQuery() {
  filters.orgId = (route.query.orgId as string) || null;
  filters.systemId = (route.query.systemId as string) || null;
  filters.systemName = (route.query.systemName as string) || null;
  filters.domain = (route.query.domain as string) || null;
  filters.level = (route.query.level as string) || null;
  filters.status = ((route.query.status as SafetyRiskStatus | 'ACTIVE') ||
    (route.query.activeOnly === 'true' ? 'ACTIVE' : 'PENDING_CONFIRMATION')) as SafetyRiskStatus | 'ACTIVE';
  filters.majorOnly = route.query.majorOnly === 'true';
  filters.locationId = (route.query.locationId as string) || null;
  filters.deviceId = (route.query.deviceId as string) || null;
  filters.assigneeId = (route.query.assigneeId as string) || null;
  const from = Number(route.query.from);
  const to = Number(route.query.to);
  filters.range = Number.isFinite(from) && from > 0 ? [from, Number.isFinite(to) && to > 0 ? to : Date.now()] : null;
}
async function fetchRisks(params: Record<string, unknown>) {
  if (!canSelect.value) return;
  error.value = '';
  try {
    return await safetyApi.risks(params);
  } catch (cause: any) {
    error.value = cause?.message || '风险列表加载失败，请稍后重试。';
    throw cause;
  }
}
function search() {
  selectedIds.value = [];
  tableRef.value?.handleQuery?.();
}
function handleCheckedRows(keys: Array<string | number>) {
  selectedIds.value = keys.map(String);
}
function reset() {
  Object.assign(filters, {
    orgId: null,
    systemId: null,
    systemName: null,
    domain: null,
    level: null,
    status: 'PENDING_CONFIRMATION',
    majorOnly: false,
    locationId: null,
    deviceId: null,
    assigneeId: null,
    range: null,
  });
  void handleVenueChange();
}
async function loadCatalogs() {
  catalogs.value = filters.orgId
    ? unwrapResponse<RecordFilterOptionsVo>(await ingestApi.recordFilterOptions({ orgId: filters.orgId }))
    : { devices: [], locations: [] };
}
async function handleVenueChange() {
  filters.systemId = null;
  filters.systemName = null;
  filters.locationId = null;
  filters.deviceId = null;
  selectedIds.value = [];
  await router.replace({
    query: {
      ...route.query,
      orgId: filters.orgId || undefined,
      systemId: undefined,
      systemName: undefined,
      locationId: undefined,
      deviceId: undefined,
      id: undefined,
    },
  });
  try {
    await loadCatalogs();
    catalogError.value = '';
  } catch {
    catalogs.value = { devices: [], locations: [] };
    catalogError.value = '当前场馆设备和位置目录加载失败，仍可按其他条件查询风险。';
  }
  tableRef.value?.handleQuery?.();
}
async function clearSystemFilter() {
  filters.systemId = null;
  filters.systemName = null;
  selectedIds.value = [];
  await router.replace({ query: { ...route.query, systemId: undefined, systemName: undefined } });
  tableRef.value?.handleQuery?.();
}
function openDetail(id: string) {
  if (!canSelect.value) return;
  detailId.value = id;
  detailVisible.value = true;
}
function refreshList() {
  tableRef.value?.handleRefresh?.();
}
async function batchAssign() {
  submitting.value = true;
  try {
    await safetyApi.batchAssign(selectedIds.value, batchAssignee.value);
    message.success('批量指派成功');
    selectedIds.value = [];
    tableRef.value?.handleRefresh?.();
  } catch (cause: any) {
    error.value = cause?.message || '批量指派失败。';
  } finally {
    submitting.value = false;
  }
}
async function exportCurrent() {
  if (!canSelect.value) return;
  try {
    const result: any = unwrapResponse<any>(await safetyApi.risks(queryParams(true)));
    const data: SafetyRiskVo[] = Array.isArray(result) ? result : result?.list || [];
    const lines = [
      ['场馆', '领域', '等级', '状态', '风险', '设备', '触发位置', '当前位置', '责任人', '最近发生'],
      ...data.map((row) => [
        venueName(row.orgId),
        domainLabel(row.domain),
        levelLabel(row.level),
        statusLabel(row.status),
        row.title,
        row.deviceName || row.deviceId,
        row.locationSnapshotPath || '',
        row.currentLocationPath || '',
        row.assigneeId || '',
        formatDateTime(row.lastOccurredAt),
      ]),
    ];
    const csv = `\uFEFF${lines.map((line) => line.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(',')).join('\n')}`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `场馆安全风险-${Date.now()}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (cause: any) {
    error.value = cause?.message || '导出失败。';
  }
}
function domainLabel(value: string) {
  return domainOptions.find((item) => item.value === value)?.label || value;
}
function venueName(value: string) {
  return (
    venues.value.find((item) => item.id === value)?.shortName ||
    venues.value.find((item) => item.id === value)?.name ||
    value
  );
}
function levelLabel(value: string) {
  return levelOptions.find((item) => item.value === value)?.label || value;
}
function statusLabel(value: string) {
  return statusOptions.find((item) => item.value === value)?.label || value;
}
watch(
  () => route.query.id,
  (id, previous) => {
    if (route.path === '/museum-safety/risk' && id && id !== previous && canSelect.value) void openDetail(String(id));
  },
);
onMounted(async () => {
  if (!canSelect.value) return;
  try {
    venues.value = await loadVenueCatalog();
    if (!filters.orgId && venues.value.length === 1) filters.orgId = venues.value[0].id;
    await loadCatalogs();
  } catch {
    catalogs.value = { devices: [], locations: [] };
    catalogError.value = '设备和位置目录加载失败，仍可按其他条件查询风险。';
  }
  tableRef.value?.handleQuery?.();
  if (route.query.id) await openDetail(String(route.query.id));
});
onActivated(() => {
  if (firstActivation) {
    firstActivation = false;
    return;
  }
  if (!canSelect.value) return;
  applyRouteQuery();
  void loadCatalogs()
    .catch(() => {
      catalogs.value = { devices: [], locations: [] };
      catalogError.value = '设备和位置目录加载失败，仍可按其他条件查询风险。';
    })
    .then(() => tableRef.value?.handleRefresh?.())
    .then(async () => {
      if (route.query.id) await openDetail(String(route.query.id));
      else {
        detailVisible.value = false;
        detailId.value = '';
      }
    });
});
</script>

<style scoped>
.risk-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 12px;
  box-sizing: border-box;
  overflow: hidden;
}
.status-tabs {
  flex: none;
  margin-bottom: 12px;
}
.safety-page-table {
  min-height: 0;
  flex: 1;
}
.state-alert {
  margin-bottom: 12px;
}
.empty {
  padding: 36px 0;
}
</style>
