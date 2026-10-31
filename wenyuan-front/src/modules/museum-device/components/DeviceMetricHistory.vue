<template>
  <div class="device-metric-history">
    <NTabs v-model:value="activeTab" type="line" animated @update:value="onTabChange">
      <NTabPane name="history" :tab="selectedMetricIsNumeric ? '历史曲线' : '历史数据'" display-directive="show">
        <div class="history-toolbar">
          <NSelect
            v-model:value="selectedKey"
            :options="metricOptions"
            filterable
            placeholder="选择指标"
            class="history-toolbar__metric"
            @update:value="onMetricChange"
          />
          <NDatePicker v-model:value="range" type="datetimerange" clearable class="history-toolbar__range" />
          <NSelect
            v-if="selectedMetricIsNumeric"
            v-model:value="aggregation"
            :options="aggregationOptions"
            class="history-toolbar__aggregation"
          />
          <NButton type="primary" :loading="historyLoading" @click="loadHistory(true)">查询</NButton>
        </div>

        <NSpin :show="historyLoading">
          <template v-if="selectedMetricIsNumeric">
            <template v-if="series?.points?.length">
              <NGrid responsive="screen" :cols="'1 s:2 m:4'" :x-gap="12" :y-gap="12" class="stat-grid">
                <NGridItem><NStatistic label="最新值" :value="displayNumber(series.latestValue)" /></NGridItem>
                <NGridItem
                  ><NStatistic :label="seriesSummaryLabel" :value="displayNumber(seriesSummaryValue)"
                /></NGridItem>
                <NGridItem><NStatistic label="最小值" :value="displayNumber(series.minValue)" /></NGridItem>
                <NGridItem><NStatistic label="最大值" :value="displayNumber(series.maxValue)" /></NGridItem>
              </NGrid>
              <div class="series-meta">
                <span>{{ series.name || selectedMetric?.name || series.key }}</span>
                <span>共 {{ series.total }} 个有效数据点</span>
                <span v-if="series.bucketMinutes">每 {{ bucketLabel(series.bucketMinutes) }} 聚合</span>
                <span v-if="series.sampled">曲线已均匀抽样，统计值仍按全部数据计算</span>
              </div>
              <MetricSeriesChart :data="series" />
            </template>
            <NEmpty v-else :description="historyEmptyDescription" class="history-empty" />
          </template>
          <template v-else>
            <div v-if="stateHistoryPagination.itemCount" class="state-history-meta">
              共 {{ stateHistoryPagination.itemCount }} 条历史数据
            </div>
            <NDataTable
              v-if="stateHistoryRows.length"
              remote
              :columns="stateHistoryColumns"
              :data="stateHistoryRows"
              :pagination="stateHistoryPagination"
              :row-key="(row: IngestRecordVo) => row.id"
              :max-height="420"
              :scroll-x="1080"
              size="small"
              @update:page="changeStateHistoryPage"
              @update:page-size="changeStateHistoryPageSize"
            />
            <NEmpty v-else description="所选时间范围内暂无历史数据" class="history-empty" />
          </template>
        </NSpin>
      </NTabPane>

      <NTabPane name="latest" tab="实时数据" display-directive="show">
        <div class="latest-toolbar">
          <span>每个指标展示当前最新数据</span>
          <NButton size="small" :loading="latestLoading" @click="loadLatest">刷新实时数据</NButton>
        </div>
        <NDataTable :columns="latestColumns" :data="latestRows" :pagination="false" :max-height="360" :scroll-x="900" size="small" />
        <NEmpty v-if="!latestLoading && !latestRows.length" description="暂无实时数据" class="latest-empty" />
      </NTabPane>
    </NTabs>
    <IngestRecordDetails v-model:show="detailsVisible" :record="detailsRecord" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref, watch } from 'vue';
import {
  NButton,
  NDataTable,
  NDatePicker,
  NEmpty,
  NGrid,
  NGridItem,
  NSelect,
  NSpin,
  NStatistic,
  NTabPane,
  NTabs,
  NTag,
  type DataTableColumns,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { ingestApi } from '../api/ingest';
import type { IngestRecordVo, MetricSeriesMode, MuseumMetricSeriesVo, MuseumSpatialMetricVo } from '../typings';
import MetricSeriesChart from './MetricSeriesChart.vue';
import IngestRecordDetails from './IngestRecordDetails.vue';
import { recordSummary, recordTarget } from '../utils/recordDisplay';
import { metricDisplayName } from '../utils/metricName';
import { aggregationLabel } from '../utils/comparison';
import { formatMetricValue } from '../utils/metricValue';
import { sortSpatialMetrics } from '../utils/spatialMetricIcon';

const props = withDefaults(
  defineProps<{
    deviceId: string;
    metrics?: MuseumSpatialMetricVo[];
    defaultMetricKey?: string | null;
    defaultRangeHours?: number;
    defaultRange?: [number, number] | null;
  }>(),
  {
    metrics: () => [],
    defaultMetricKey: null,
    defaultRangeHours: 24,
    defaultRange: null,
  },
);
const message = useMessage();

type AggregationValue = 'RAW' | 'AUTO' | '5' | '15' | '60' | '360' | '1440';

const activeTab = ref<'history' | 'latest'>('history');
const selectedKey = ref<string | null>(null);
const now = Date.now();
const range = ref<[number, number] | null>(
  props.defaultRange ? [...props.defaultRange] : [now - props.defaultRangeHours * 3600 * 1000, now],
);
const aggregation = ref<AggregationValue>('AUTO');
const seriesLoading = ref(false);
const stateHistoryLoading = ref(false);
const latestLoading = ref(false);
const series = ref<MuseumMetricSeriesVo | null>(null);
const stateHistoryRows = ref<IngestRecordVo[]>([]);
type LatestMetric = MuseumSpatialMetricVo & { record?: IngestRecordVo };
const latestRows = ref<LatestMetric[]>([]);
const detailsVisible = ref(false);
const detailsRecord = ref<IngestRecordVo | null>(null);
const stateHistoryPagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [20, 50, 100],
});
let initializationRequest = 0;
let seriesRequest = 0;
let stateHistoryRequest = 0;
let latestRequest = 0;

const aggregationOptions = [
  { label: '自动统计粒度', value: 'AUTO' },
  { label: '原始采样点', value: 'RAW' },
  { label: '每 5 分钟', value: '5' },
  { label: '每 15 分钟', value: '15' },
  { label: '每小时', value: '60' },
  { label: '每 6 小时', value: '360' },
  { label: '每天', value: '1440' },
];

const metricCatalog = computed(() => {
  const metrics = new Map<string, MuseumSpatialMetricVo>();
  [...props.metrics, ...latestRows.value].forEach((metric) => {
    if (!hasMetricValue(metric)) return;
    metrics.set(metric.key, metric);
  });
  return sortSpatialMetrics([...metrics.values()]);
});
const metricOptions = computed(() =>
  metricCatalog.value.map((metric) => ({
    label: `${metric.name || metric.key}${metric.unit ? ` (${metric.unit})` : ''}`,
    value: metric.key,
  })),
);
const selectedMetric = computed(() => metricCatalog.value.find((metric) => metric.key === selectedKey.value));
const selectedMetricIsNumeric = computed(
  () => selectedMetric.value?.numericValue !== null && selectedMetric.value?.numericValue !== undefined,
);
const historyLoading = computed(() =>
  selectedMetricIsNumeric.value ? seriesLoading.value : stateHistoryLoading.value,
);
const historyEmptyDescription = computed(() =>
  metricOptions.value.length ? '所选时间范围内暂无有效数值数据' : '该设备暂无可绘制曲线的数值指标',
);
const seriesSummaryLabel = computed(() => aggregationLabel(series.value?.aggregation || 'GAUGE'));
const seriesSummaryValue = computed(() => {
  if (series.value?.aggregation === 'INCREMENT') return series.value.sumValue ?? series.value.averageValue;
  if (series.value?.aggregation === 'CUMULATIVE' || series.value?.aggregation === 'STATE') {
    return series.value.latestValue;
  }
  return series.value?.averageValue;
});

function unwrapResponse(response: any): any {
  let payload = response;
  for (let depth = 0; depth < 5; depth += 1) {
    if (payload === null || typeof payload !== 'object' || !('data' in payload)) return payload;
    payload = payload.data;
  }
  return payload;
}

function responseList<T>(response: any): T[] {
  const payload = unwrapResponse(response);
  if (Array.isArray(payload)) return payload;
  return Array.isArray(payload?.list) ? payload.list : [];
}

function syncSelectedMetric() {
  const preferred = props.defaultMetricKey || selectedKey.value;
  selectedKey.value = metricOptions.value.some((item) => item.value === preferred)
    ? preferred
    : metricOptions.value[0]?.value || null;
}

async function initialize() {
  const requestId = ++initializationRequest;
  seriesRequest += 1;
  stateHistoryRequest += 1;
  series.value = null;
  stateHistoryRows.value = [];
  stateHistoryPagination.page = 1;
  stateHistoryPagination.itemCount = 0;
  latestRows.value = sortSpatialMetrics(props.metrics);
  syncSelectedMetric();
  await loadLatest();
  if (requestId !== initializationRequest) return;
  syncSelectedMetric();
  await loadHistory(true);
}

async function loadLatest() {
  if (!props.deviceId) return;
  const requestId = ++latestRequest;
  const deviceId = props.deviceId;
  latestLoading.value = true;
  try {
    const rows = responseList<IngestRecordVo>(await ingestApi.latestRecords({ deviceId, pageNum: 1, pageSize: 0 }));
    if (requestId !== latestRequest || deviceId !== props.deviceId) return;
    latestRows.value = sortSpatialMetrics(
      rows.map((row) => ({
        record: row,
        key: row.key,
        name: metricDisplayName(row.key, row.metricName, row.sourceMetricName),
        numericValue: row.numericValue,
        stringValue: row.stringValue,
        booleanValue: row.booleanValue,
        unit: row.metricUnit || row.unit,
        occurredAt: row.occurredAt,
        receivedAt: row.receivedAt,
      })),
    );
    syncSelectedMetric();
  } catch {
    if (requestId !== latestRequest) return;
    latestRows.value = sortSpatialMetrics(props.metrics);
    message.warning('实时数据加载失败');
  } finally {
    if (requestId === latestRequest) latestLoading.value = false;
  }
}

async function loadSeries() {
  if (!selectedMetricIsNumeric.value || !props.deviceId || !selectedKey.value || !range.value) {
    series.value = null;
    return;
  }
  const requestId = ++seriesRequest;
  seriesLoading.value = true;
  try {
    const mode: MetricSeriesMode = aggregation.value === 'RAW' ? 'RAW' : 'STATISTICS';
    const payload = unwrapResponse(
      await ingestApi.recordSeries({
        deviceId: props.deviceId,
        key: selectedKey.value,
        from: range.value[0],
        to: range.value[1],
        mode,
        bucketMinutes:
          aggregation.value === 'AUTO' || aggregation.value === 'RAW' ? undefined : Number(aggregation.value),
        maxPoints: 1000,
      }),
    );
    if (requestId !== seriesRequest) return;
    series.value = payload ? { ...payload, points: Array.isArray(payload.points) ? payload.points : [] } : null;
  } catch {
    if (requestId === seriesRequest) series.value = null;
    message.warning('历史曲线加载失败');
  } finally {
    if (requestId === seriesRequest) seriesLoading.value = false;
  }
}

async function loadStateHistory(resetPage = false) {
  if (resetPage) stateHistoryPagination.page = 1;
  if (!props.deviceId || !selectedKey.value || !range.value) {
    stateHistoryRows.value = [];
    stateHistoryPagination.itemCount = 0;
    return;
  }
  const requestId = ++stateHistoryRequest;
  stateHistoryLoading.value = true;
  try {
    const payload = unwrapResponse(
      await ingestApi.records({
        deviceId: props.deviceId,
        key: selectedKey.value,
        from: range.value[0],
        to: range.value[1],
        pageNum: stateHistoryPagination.page,
        pageSize: stateHistoryPagination.pageSize,
      }),
    );
    if (requestId !== stateHistoryRequest) return;
    const rows = Array.isArray(payload?.list) ? payload.list : Array.isArray(payload) ? payload : [];
    stateHistoryRows.value = rows;
    stateHistoryPagination.itemCount = Number(payload?.total ?? rows.length);
  } catch {
    if (requestId !== stateHistoryRequest) return;
    stateHistoryRows.value = [];
    stateHistoryPagination.itemCount = 0;
    message.warning('历史数据加载失败');
  } finally {
    if (requestId === stateHistoryRequest) stateHistoryLoading.value = false;
  }
}

async function loadHistory(resetPage = false) {
  if (selectedMetricIsNumeric.value) {
    stateHistoryRequest += 1;
    stateHistoryLoading.value = false;
    stateHistoryRows.value = [];
    stateHistoryPagination.itemCount = 0;
    await loadSeries();
    return;
  }
  seriesRequest += 1;
  seriesLoading.value = false;
  series.value = null;
  await loadStateHistory(resetPage);
}

function onMetricChange() {
  seriesRequest += 1;
  stateHistoryRequest += 1;
  seriesLoading.value = false;
  stateHistoryLoading.value = false;
  series.value = null;
  stateHistoryRows.value = [];
  stateHistoryPagination.page = 1;
  stateHistoryPagination.itemCount = 0;
}

function changeStateHistoryPage(page: number) {
  stateHistoryPagination.page = page;
  void loadStateHistory();
}

function changeStateHistoryPageSize(pageSize: number) {
  stateHistoryPagination.pageSize = pageSize;
  stateHistoryPagination.page = 1;
  void loadStateHistory();
}

function onTabChange(value: string) {
  if (value === 'latest') loadLatest();
}

function displayNumber(value?: number | null) {
  return formatMetricValue({ numericValue: value, unit: series.value?.unit });
}

function bucketLabel(minutes: number) {
  if (minutes % 1440 === 0) return `${minutes / 1440} 天`;
  if (minutes % 60 === 0) return `${minutes / 60} 小时`;
  return `${minutes} 分钟`;
}

function hasMetricValue(metric: MuseumSpatialMetricVo) {
  return (
    (metric.numericValue !== null && metric.numericValue !== undefined) ||
    (metric.booleanValue !== null && metric.booleanValue !== undefined) ||
    (metric.stringValue !== null && metric.stringValue !== undefined)
  );
}

const stateHistoryColumns: DataTableColumns<IngestRecordVo> = [
  { title: '发生时间', key: 'occurredAt', width: 185, render: (row) => formatDateTime(row.occurredAt) },
  {
    title: '值 / 事件摘要',
    key: 'value',
    minWidth: 280,
    render: (row) => h('strong', recordSummary(row)),
  },
  { title: '对应目标', key: 'target', minWidth: 240, render: (row) => recordTarget(row) || '--' },
  {
    title: '数据质量',
    key: 'quality',
    width: 100,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          bordered: false,
          type: row.quality === 'NORMAL' ? 'success' : row.quality === 'SUSPECT' ? 'warning' : 'error',
        },
        { default: () => ({ NORMAL: '正常', SUSPECT: '可疑', INVALID: '无效' })[row.quality] || row.quality },
      ),
  },
  { title: '是否回灌', key: 'backfill', width: 90, render: (row) => (row.backfill ? '是' : '否') },
  { title: '接收时间', key: 'receivedAt', width: 185, render: (row) => formatDateTime(row.receivedAt) },
  { title: '详情', key: 'details', width: 80, fixed: 'right', render: detailButton },
];

const latestColumns: DataTableColumns<LatestMetric> = [
  { title: '指标', key: 'name', minWidth: 180, render: (row) => row.name || row.key },
  { title: '最新值 / 事件摘要', key: 'value', minWidth: 280, render: (row) => h('strong', row.record ? recordSummary(row.record) : formatMetricValue(row)) },
  {
    title: '类型',
    key: 'type',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'small', bordered: false },
        { default: () => (row.numericValue !== null && row.numericValue !== undefined ? '数值' : '状态') },
      ),
  },
  { title: '发生时间', key: 'occurredAt', width: 185, render: (row) => formatDateTime(row.occurredAt) },
  { title: '详情', key: 'details', width: 80, fixed: 'right', render: (row) => row.record ? detailButton(row.record) : '--' },
];

function detailButton(record: IngestRecordVo) {
  return h(NButton, { text: true, type: 'primary', onClick: () => {
    detailsRecord.value = record; detailsVisible.value = true;
  } }, { default: () => '查看详情' });
}

watch(() => props.deviceId, initialize, { immediate: true });
watch(
  () => props.metrics,
  (metrics) => {
    if (!latestLoading.value) latestRows.value = sortSpatialMetrics(metrics);
    syncSelectedMetric();
  },
  { deep: true },
);

defineExpose({ refresh: initialize, loadHistory, loadSeries, loadStateHistory, loadLatest });
</script>

<style scoped>
.device-metric-history {
  min-height: 430px;
}
.history-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.history-toolbar__metric {
  width: 220px;
}
.history-toolbar__range {
  width: 350px;
}
.history-toolbar__aggregation {
  width: 160px;
}
.stat-grid {
  margin-bottom: 12px;
}
.stat-grid :deep(.n-grid-item) {
  padding: 10px 14px;
  border: 1px solid #edf0f3;
  border-radius: 6px;
  background: #fafbfc;
}
.series-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  margin: 4px 4px 0;
  color: #777;
  font-size: 12px;
}
.series-meta span:first-child {
  color: #333;
  font-weight: 600;
}
.state-history-meta {
  margin: 0 4px 10px;
  color: #777;
  font-size: 12px;
}
.history-empty {
  padding: 120px 0;
}
.latest-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #777;
  font-size: 13px;
}
.latest-empty {
  padding: 80px 0;
}

@media (max-width: 900px) {
  .history-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .history-toolbar__metric,
  .history-toolbar__range,
  .history-toolbar__aggregation {
    width: 100%;
  }
}
</style>
