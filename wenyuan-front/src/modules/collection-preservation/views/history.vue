<template>
  <HPageDataTable
    ref="tableRef"
    v-model:data="tableRows"
    :clear-selection-on-fetch="true"
    title="历史数据"
    :columns="columns"
    :fetch-data="fetchData"
    :params="queryParams"
    row-key-field="id"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :show-operate="false"
    :scroll-x="780"
    @row-view="openHistoryView"
  >
    <template #toolbar-extra>
      <NButton size="small" :loading="catalogLoading" @click="reloadPage">刷新目录与数据</NButton>
      <NButton size="small" type="primary" :loading="trendLoading" @click="openTrend"> 查看趋势 </NButton>
    </template>

    <template #queryForm>
      <NAlert v-if="listError || catalogError" type="error" :bordered="false">{{ listError || catalogError }}</NAlert>
      <NFormItem label="监测位置">
        <NTreeSelect
          v-model:value="queryParams.sensorPointId"
          :options="pointOptions"
          key-field="id"
          label-field="name"
          children-field="children"
          check-strategy="all"
          filterable
          clearable
          placeholder="请选择监测位置"
          style="width: 240px"
        />
      </NFormItem>
      <NFormItem label="指标">
        <NSelect
          v-model:value="queryParams.sensorId"
          :options="sensorOptions"
          filterable
          clearable
          placeholder="请选择指标"
          style="width: 200px"
        />
      </NFormItem>
      <NFormItem label="数据质量">
        <NSelect
          v-model:value="queryParams.quality"
          :options="qualityOptions"
          clearable
          placeholder="全部"
          style="width: 140px"
        />
      </NFormItem>
      <NFormItem label="采集时间">
        <NDatePicker
          v-model:value="range"
          type="datetimerange"
          clearable
          style="width: 340px"
          @update:value="onRangeChange"
        />
      </NFormItem>
    </template>

    <HPageView v-model:show="historyViewShow" title="历史数据详情" width="640px" :show-action="false">
      <NAlert v-if="historyViewError" type="error" :bordered="false">{{ historyViewError }}</NAlert>
      <NSpin :show="historyViewLoading">
        <NDescriptions v-if="historyViewRow" label-placement="left" :column="2" bordered size="small">
          <NDescriptionsItem label="采集时间"
            ><span style="white-space: nowrap">{{ formatDateTime(historyViewRow.dateTime) }}</span></NDescriptionsItem
          >
          <NDescriptionsItem label="接收时间"
            ><span style="white-space: nowrap">{{ formatDateTime(historyViewRow.receivedAt) }}</span></NDescriptionsItem
          >
          <NDescriptionsItem label="监测位置">{{
            historyViewRow.sensorPointName || historyViewRow.sensorPointId || '--'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="位置依据">{{
            hasLocationAssociation(historyViewRow.sampleLocationBasis)
              ? locationAssociationLabel(historyViewRow.sampleLocationBasis)
              : '历史位置待核实，不能作为该场所的环境证明'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="指标">{{
            historyViewRow.sensorName || historyViewRow.sensorId || '--'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="监测值"
            >{{ historyViewRow.dataValue ?? '--' }} {{ historyViewRow.sensorUnit || '' }}</NDescriptionsItem
          >
          <NDescriptionsItem label="数据质量">{{ qualityLabel(historyViewRow.quality) }}</NDescriptionsItem>
          <NDescriptionsItem label="来源">{{ sourceLabel(historyViewRow.sourceType) }}</NDescriptionsItem>
          <NDescriptionsItem label="补传">{{ historyViewRow.backfill ? '是' : '否' }}</NDescriptionsItem>
          <NDescriptionsItem label="网关 ID">{{ historyViewRow.gatewayId || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="设备 ID">{{ historyViewRow.deviceId || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="来源消息 ID" :span="2">{{
            historyViewRow.sourceMessageId || '--'
          }}</NDescriptionsItem>
        </NDescriptions>
      </NSpin>
    </HPageView>

    <HPageView v-model:show="trendShow" :title="trendTitle" width="920px" :show-action="false">
      <NAlert v-if="trendError" type="error" :bordered="false">{{ trendError }}</NAlert>
      <NSpin :show="trendLoading">
        <SensorTrendChart v-if="series.length" :data="series" :unit="currentUnit" height="420px" />
        <NEmpty v-else-if="!trendError && !trendLoading" description="所选时间范围内暂无历史数据" />
      </NSpin>
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onBeforeUnmount, onActivated, onDeactivated, reactive, ref, watch } from 'vue';
import {
  NAlert,
  NButton,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NFormItem,
  NSelect,
  NSpin,
  NTreeSelect,
  useMessage,
} from 'naive-ui';
import { formatDateTime, transformToTreeFormat } from '@/utils';
import { historyApi } from '../api/history';
import { hasLocationAssociation, locationAssociationLabel } from '../utils/environmentSample';
import { sensorApi } from '../api/sensor';
import { sensorPointApi } from '../api/sensorPoint';
import SensorTrendChart from '../components/SensorTrendChart.vue';
import type { HistoryDataVo, SensorPointVo, SensorVo } from '../typings';

const message = useMessage();
const tableRef = ref<any>(null);
const tableRows = ref<HistoryDataVo[]>([]);
const listError = ref('');
const catalogError = ref('');
const catalogLoading = ref(false);
let active = true,
  disposed = false;
let listGeneration = 0,
  viewGeneration = 0,
  trendGeneration = 0,
  catalogGeneration = 0;
const available = () => active && !disposed;
function payload(response: any) {
  return response?.data?.data ?? response?.data ?? response;
}
function rowsFrom(response: any): any[] {
  const data = payload(response);
  const rows = data?.list ?? data;
  if (!Array.isArray(rows)) throw new Error('返回数据格式异常');
  return rows;
}
const now = Date.now();
const range = ref<[number, number] | null>([now - 24 * 3600 * 1000, now]);
const queryParams = reactive({
  sensorPointId: null as string | null,
  sensorId: null as string | null,
  from: range.value![0] as number | null,
  to: range.value![1] as number | null,
  quality: null as string | null,
});

const points = ref<SensorPointVo[]>([]);
const pointOptions = computed(() =>
  transformToTreeFormat(
    points.value.map((point) => ({ ...point })),
    { pIdKey: 'parentId' },
  ),
);
const sensors = ref<SensorVo[]>([]);
const sensorOptions = computed(() =>
  sensors.value.map((sensor) => ({
    label: `${sensor.name}${sensor.unit ? ` (${sensor.unit})` : ''}`,
    value: sensor.id!,
  })),
);
const currentSensor = computed(() => sensors.value.find((sensor) => sensor.id === queryParams.sensorId));
const currentUnit = ref('');

const columns = [
  {
    title: '采集时间',
    key: 'dateTime',
    width: 190,
    resizable: true,
    columnType: 'dateTime',
    ellipsis: { tooltip: true },
  },
  {
    title: '位置依据',
    key: 'sampleLocationBasis',
    width: 170,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => locationAssociationLabel(row.sampleLocationBasis),
  },
  {
    title: '监测位置',
    key: 'sensorPointName',
    minWidth: 180,
    resizable: true,
    width: 180,
    columnType: 'title',
    ellipsis: { tooltip: true },
  },
  { title: '指标', key: 'sensorName', minWidth: 140, resizable: true, width: 140, ellipsis: { tooltip: true } },
  { title: '监测值', key: 'dataValue', width: 120, render: (row: HistoryDataVo) => row.dataValue ?? '--' },
  {
    title: '单位',
    key: 'sensorUnit',
    width: 110,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => row.sensorUnit || '--',
  },
  {
    title: '质量',
    key: 'quality',
    width: 120,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => qualityLabel(row.quality),
  },
  {
    title: '来源',
    key: 'sourceType',
    width: 130,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => sourceLabel(row.sourceType),
  },
  {
    title: '接收时间',
    key: 'receivedAt',
    width: 190,
    resizable: true,
    columnType: 'dateTime',
    ellipsis: { tooltip: true },
  },
  {
    title: '补传',
    key: 'backfill',
    width: 90,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => (row.backfill ? '是' : '否'),
  },
  {
    title: '操作',
    key: 'detail',
    width: 96,
    fixed: 'right' as const,
    render: (row: HistoryDataVo) =>
      h(NButton, { text: true, type: 'primary', onClick: () => openHistoryView(row) }, () => '查看详情'),
  },
];

const qualityOptions = [
  { label: '正常', value: 'NORMAL' },
  { label: '可疑', value: 'SUSPECT' },
  { label: '无效', value: 'INVALID' },
];
function qualityLabel(value?: string) {
  return qualityOptions.find((item) => item.value === value)?.label || value || '--';
}
function sourceLabel(value?: string) {
  return (
    ({ DEVICE: '设备', MANUAL: '人工', IMPORT: '导入', DERIVED: '衍生' } as Record<string, string>)[value || ''] ||
    value ||
    '--'
  );
}

const historyViewShow = ref(false);
const historyViewRow = ref<HistoryDataVo | null>(null);
const historyViewLoading = ref(false);
const historyViewError = ref('');
const trendShow = ref(false);
const trendLoading = ref(false);
const trendError = ref('');
const series = ref<HistoryDataVo[]>([]);
const trendTitle = ref('历史趋势');

function clearView() {
  ++viewGeneration;
  historyViewRow.value = null;
  historyViewLoading.value = false;
  historyViewError.value = '';
  historyViewShow.value = false;
}
function clearTrend() {
  ++trendGeneration;
  series.value = [];
  trendLoading.value = false;
  trendError.value = '';
  trendShow.value = false;
  currentUnit.value = '';
}
function clearResults() {
  ++listGeneration;
  tableRows.value = [];
  clearView();
  clearTrend();
}
function clearCatalogs() {
  ++catalogGeneration;
  points.value = [];
  sensors.value = [];
  catalogLoading.value = false;
}
async function openHistoryView(row: HistoryDataVo) {
  if (!available()) return;
  clearView();
  historyViewShow.value = true;
  if (!row.id || !row.orgId) {
    historyViewError.value = '历史记录身份不完整，请重新查询';
    return;
  }
  const id = row.id,
    orgId = row.orgId,
    generation = viewGeneration;
  historyViewLoading.value = true;
  try {
    const data = payload(await historyApi.get(id));
    if (!available() || generation !== viewGeneration || !historyViewShow.value) return;
    if (!data || data.id !== id || data.orgId !== orgId) throw new Error('历史记录已不可读或身份不一致，请重新查询');
    historyViewRow.value = data;
  } catch (error: any) {
    if (available() && generation === viewGeneration && historyViewShow.value) {
      ++listGeneration;
      tableRows.value = [];
      clearCatalogs();
      clearTrend();
      historyViewRow.value = null;
      historyViewError.value = error?.message || '历史详情读取失败';
    }
  } finally {
    if (available() && generation === viewGeneration) historyViewLoading.value = false;
  }
}

async function fetchData(params: any) {
  if (!available()) throw new Error('页面已离开');
  const generation = ++listGeneration;
  tableRows.value = [];
  clearView();
  clearTrend();
  listError.value = '';
  try {
    const response = await historyApi.page({ ...params, ...queryParams });
    if (!available() || generation !== listGeneration) throw new Error('列表请求已失效');
    rowsFrom(response);
    return response;
  } catch (error: any) {
    if (available() && generation === listGeneration) {
      clearResults();
      clearCatalogs();
      listError.value = error?.message || '历史列表读取失败';
    }
    throw error;
  }
}

function onRangeChange(value: [number, number] | null) {
  queryParams.from = value?.[0] ?? null;
  queryParams.to = value?.[1] ?? null;
}

async function loadCatalogs(): Promise<boolean> {
  if (!available()) return false;
  clearCatalogs();
  catalogError.value = '';
  catalogLoading.value = true;
  const generation = catalogGeneration;
  try {
    const [pointResponse, sensorResponse] = await Promise.all([sensorPointApi.page({ pageSize: 0 }), sensorApi.all()]);
    if (!available() || generation !== catalogGeneration) return false;
    const pointRows = rowsFrom(pointResponse),
      sensorRows = rowsFrom(sensorResponse);
    points.value = pointRows;
    sensors.value = sensorRows;
    if (queryParams.sensorPointId && !pointRows.some((row) => row.id === queryParams.sensorPointId))
      queryParams.sensorPointId = null;
    if (queryParams.sensorId && !sensorRows.some((row) => row.id === queryParams.sensorId)) queryParams.sensorId = null;
    return true;
  } catch (error: any) {
    if (available() && generation === catalogGeneration) {
      clearResults();
      clearCatalogs();
      catalogError.value = error?.message || '筛选目录读取失败';
    }
    return false;
  } finally {
    if (available() && generation === catalogGeneration) catalogLoading.value = false;
  }
}

async function reloadPage() {
  if (!available()) return;
  clearResults();
  listError.value = '';
  if (await loadCatalogs()) tableRef.value?.handleRefresh();
}

async function openTrend() {
  if (!available()) return;
  clearTrend();
  const point = points.value.find((item) => item.id === queryParams.sensorPointId);
  const sensor = currentSensor.value;
  if (!point?.id || !sensor?.id || !range.value) {
    message.warning('请选择当前目录中的监测位置、指标与时间范围');
    return;
  }
  const params = {
    sensorPointId: point.id,
    sensorId: sensor.id,
    from: queryParams.from ?? range.value[0],
    to: queryParams.to ?? range.value[1],
    limit: 2000,
  };
  trendTitle.value = `${point.name || '监测位置'} · ${sensor.name || '指标'}历史趋势`;
  currentUnit.value = sensor.unit || '';
  trendShow.value = true;
  trendLoading.value = true;
  const generation = trendGeneration;
  try {
    const response = await historyApi.series(params);
    if (!available() || generation !== trendGeneration || !trendShow.value) return;
    const rows = rowsFrom(response);
    if (rows.some((row) => row.sensorPointId !== params.sensorPointId || row.sensorId !== params.sensorId))
      throw new Error('趋势数据与当前查询不一致');
    series.value = rows;
  } catch (error: any) {
    if (available() && generation === trendGeneration && trendShow.value) {
      ++listGeneration;
      tableRows.value = [];
      clearCatalogs();
      clearView();
      series.value = [];
      trendError.value = error?.message || '历史趋势读取失败';
    }
  } finally {
    if (available() && generation === trendGeneration) trendLoading.value = false;
  }
}

watch(
  historyViewShow,
  (show) => {
    if (!show) clearView();
  },
  { flush: 'sync' },
);
watch(
  trendShow,
  (show) => {
    if (!show) clearTrend();
  },
  { flush: 'sync' },
);
watch(queryParams, () => clearResults(), { deep: true, flush: 'sync' });
function leavePage() {
  active = false;
  clearResults();
  clearCatalogs();
}
onMounted(() => {
  void loadCatalogs();
});
onDeactivated(leavePage);
onActivated(() => {
  if (disposed || active) return;
  active = true;
  void reloadPage();
});
onBeforeUnmount(() => {
  disposed = true;
  leavePage();
});
</script>
