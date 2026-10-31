<template>
  <div class="analysis-page">
    <NCard
      :bordered="false"
      class="analysis-card"
      content-style="display:flex;flex:1;min-height:0;flex-direction:column;"
    >
      <template #header>
        <div>
          <div class="analysis-title">数据分析</div>
          <div class="analysis-subtitle">选择不同设备和数值指标，在同一时间基准下比较趋势与时间桶数据</div>
        </div>
      </template>

      <div class="analysis-toolbar">
        <div class="analysis-toolbar__field analysis-toolbar__range">
          <span class="analysis-toolbar__label">时间范围</span>
          <NDatePicker v-model:value="range" type="datetimerange" clearable />
        </div>
        <div class="analysis-toolbar__field">
          <span class="analysis-toolbar__label">聚合粒度</span>
          <NSelect v-model:value="aggregation" :options="aggregationOptions" style="width: 170px" />
        </div>
        <NCheckbox v-model:checked="onlyNormal">仅使用正常数据</NCheckbox>
        <NCheckbox v-model:checked="includeBackfill">包含历史回灌</NCheckbox>
        <NButton type="primary" :loading="loading" @click="loadComparison">开始对比</NButton>
      </div>

      <ComparisonSeriesEditor
        v-model="drafts"
        :devices="devices"
        :from="rangeFrom"
        :to="rangeTo"
        class="analysis-editor"
      />

      <NAlert v-if="dirty && comparison" type="warning" :bordered="false" class="analysis-alert">
        查询条件已经变化，当前结果仍是上一次查询的数据，请重新点击“开始对比”。
      </NAlert>

      <NSpin :show="loading" class="analysis-result">
        <template v-if="comparison">
          <div class="analysis-result__header">
            <div>
              <strong>对比结果</strong>
              <span>共 {{ comparison.series.length }} 条序列，每 {{ bucketLabel(comparison.bucketMinutes) }} 聚合</span>
              <span>{{ formatDateTime(comparison.from) }} 至 {{ formatDateTime(comparison.to) }}</span>
            </div>
            <NRadioGroup v-model:value="viewMode" size="small">
              <NRadioButton value="chart">曲线</NRadioButton>
              <NRadioButton value="table">表格</NRadioButton>
            </NRadioGroup>
          </div>

          <NAlert
            v-if="comparison.series.some((item) => item.locationChangedInRange)"
            type="warning"
            :bordered="false"
            class="analysis-alert"
          >
            查询范围内有设备调整过安装位置；结果仍按设备连续统计，位置名称显示设备当前位置。
          </NAlert>

          <NGrid responsive="screen" :cols="'1 s:2 m:3 l:4'" :x-gap="12" :y-gap="12" class="analysis-summary">
            <NGridItem v-for="series in comparison.series" :key="series.seriesId">
              <NCard size="small" :bordered="true" class="analysis-summary__card">
                <div class="analysis-summary__title">{{ comparisonSeriesName(series) }}</div>
                <div class="analysis-summary__path">{{ series.locationPath || series.deviceCode }}</div>
                <div class="analysis-summary__values">
                  <div>
                    <span>最新值</span>
                    <strong>{{ displayValue(series.latestValue, series.unit) }}</strong>
                  </div>
                  <div>
                    <span>{{ aggregationLabel(series.aggregation) }}</span>
                    <strong>{{ displayValue(comparisonSummaryValue(series), series.unit) }}</strong>
                  </div>
                  <div>
                    <span>覆盖率</span>
                    <strong>{{ displayCoverage(series.coverageRate) }}</strong>
                  </div>
                </div>
                <div class="analysis-summary__meta">
                  {{ series.total }} 个有效样本 · {{ series.bucketCount }}/{{ series.expectedBucketCount }} 个时间桶
                </div>
              </NCard>
            </NGridItem>
          </NGrid>

          <NAlert v-if="comparisonUnitGroups(comparison.series).length > 2" type="info" :bordered="false">
            当前包含三种以上单位，曲线已按单位拆分为多个联动面板，避免不同量纲在同一纵轴上造成误判。
          </NAlert>
          <ComparisonSeriesChart v-if="viewMode === 'chart'" :data="comparison" />
          <ComparisonSeriesTable v-else :data="comparison" />
        </template>
        <NEmpty v-else description="请至少选择两条完整的设备指标序列，然后开始对比" class="analysis-empty" />
      </NSpin>
    </NCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NDatePicker,
  NEmpty,
  NGrid,
  NGridItem,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpin,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { ingestApi } from '../api/ingest';
import ComparisonSeriesChart from '../components/ComparisonSeriesChart.vue';
import ComparisonSeriesEditor from '../components/ComparisonSeriesEditor.vue';
import ComparisonSeriesTable from '../components/ComparisonSeriesTable.vue';
import type {
  MuseumComparisonRequestVo,
  MuseumComparisonSeriesDraft,
  MuseumComparisonVo,
  RecordFilterDeviceVo,
  RecordFilterOptionsVo,
} from '../typings';
import {
  aggregationLabel,
  comparisonSeriesName,
  comparisonSummaryValue,
  comparisonUnitGroups,
} from '../utils/comparison';
import { formatMetricValue } from '../utils/metricValue';

type AggregationValue = 'AUTO' | '5' | '15' | '30' | '60' | '180' | '360' | '1440';

const message = useMessage();
const now = Date.now();
const range = ref<[number, number] | null>([now - 24 * 60 * 60 * 1000, now]);
const aggregation = ref<AggregationValue>('AUTO');
const onlyNormal = ref(false);
const includeBackfill = ref(true);
const loading = ref(false);
const dirty = ref(false);
const viewMode = ref<'chart' | 'table'>('chart');
const devices = ref<RecordFilterDeviceVo[]>([]);
const comparison = ref<MuseumComparisonVo | null>(null);
const drafts = ref<MuseumComparisonSeriesDraft[]>([
  { seriesId: 'S1', deviceId: null, key: null, fieldCode: null },
  { seriesId: 'S2', deviceId: null, key: null, fieldCode: null },
]);
let comparisonRequest = 0;

const rangeFrom = computed(() => range.value?.[0] || 0);
const rangeTo = computed(() => range.value?.[1] || 0);
const aggregationOptions = [
  { label: '自动统计粒度', value: 'AUTO' },
  { label: '每 5 分钟', value: '5' },
  { label: '每 15 分钟', value: '15' },
  { label: '每 30 分钟', value: '30' },
  { label: '每小时', value: '60' },
  { label: '每 3 小时', value: '180' },
  { label: '每 6 小时', value: '360' },
  { label: '每天', value: '1440' },
];

function unwrapResponse<T>(response: any): T | null {
  let payload = response;
  for (let depth = 0; depth < 5; depth += 1) {
    if (payload === null || typeof payload !== 'object' || !('data' in payload)) return payload as T;
    payload = payload.data;
  }
  return payload as T;
}

async function loadDevices() {
  try {
    const payload = unwrapResponse<RecordFilterOptionsVo>(await ingestApi.recordFilterOptions());
    devices.value = Array.isArray(payload?.devices) ? payload.devices : [];
  } catch {
    devices.value = [];
    message.warning('设备目录加载失败');
  }
}

async function loadComparison() {
  if (!range.value || range.value[0] >= range.value[1]) {
    message.warning('请选择有效的时间范围');
    return;
  }
  const complete = drafts.value.filter(
    (item): item is MuseumComparisonSeriesDraft & { deviceId: string; key: string; fieldCode: string } =>
      Boolean(item.deviceId && item.key && item.fieldCode),
  );
  if (complete.length < 2 || complete.length !== drafts.value.length) {
    message.warning('请为至少两条对比序列完整选择设备、指标和字段');
    return;
  }
  const pairs = complete.map((item) => `${item.deviceId}::${item.key}::${item.fieldCode}`);
  if (new Set(pairs).size !== pairs.length) {
    message.warning('不能重复选择同一设备、指标和字段');
    return;
  }

  const requestId = ++comparisonRequest;
  const payload: MuseumComparisonRequestVo = {
    from: range.value[0],
    to: range.value[1],
    bucketMinutes: aggregation.value === 'AUTO' ? undefined : Number(aggregation.value),
    maxPoints: 1000,
    qualities: onlyNormal.value ? ['NORMAL'] : ['NORMAL', 'SUSPECT'],
    includeBackfill: includeBackfill.value,
    series: complete.map((item) => ({
      seriesId: item.seriesId,
      deviceId: item.deviceId,
      key: item.key,
      fieldCode: item.fieldCode,
    })),
  };
  loading.value = true;
  try {
    const data = unwrapResponse<MuseumComparisonVo>(await ingestApi.compareAnalysis(payload));
    if (requestId !== comparisonRequest) return;
    comparison.value = data
      ? {
          ...data,
          series: Array.isArray(data.series) ? data.series : [],
          buckets: Array.isArray(data.buckets) ? data.buckets : [],
        }
      : null;
    dirty.value = false;
    if (!comparison.value?.buckets.length) message.info('所选条件下暂无有效数值数据');
  } catch {
    if (requestId === comparisonRequest) comparison.value = null;
    message.warning('数据对比查询失败');
  } finally {
    if (requestId === comparisonRequest) loading.value = false;
  }
}

function displayValue(value?: number | null, unit?: string | null) {
  return formatMetricValue({ numericValue: value, unit });
}

function displayCoverage(value: number) {
  if (!Number.isFinite(value)) return '--';
  return `${(Math.min(1, Math.max(0, value)) * 100).toFixed(1)}%`;
}

function bucketLabel(minutes: number) {
  if (minutes % 1440 === 0) return `${minutes / 1440} 天`;
  if (minutes % 60 === 0) return `${minutes / 60} 小时`;
  return `${minutes} 分钟`;
}

watch(
  [range, aggregation, onlyNormal, includeBackfill, drafts],
  () => {
    if (comparison.value) dirty.value = true;
  },
  { deep: true },
);

onMounted(() => void loadDevices());
</script>

<style scoped>
.analysis-page,
.analysis-card {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}
.analysis-card :deep(.n-card__content) {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}
.analysis-title {
  font-size: 18px;
  font-weight: 600;
}
.analysis-subtitle {
  margin-top: 3px;
  color: #888;
  font-size: 12px;
}
.analysis-toolbar {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 12px 18px;
  margin-bottom: 14px;
}
.analysis-toolbar__field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.analysis-toolbar__label {
  color: #555;
  font-size: 12px;
}
.analysis-toolbar__range {
  width: 380px;
}
.analysis-editor {
  margin-bottom: 14px;
}
.analysis-alert {
  margin-bottom: 12px;
}
.analysis-result {
  min-height: 360px;
}
.analysis-result__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #edf0f3;
}
.analysis-result__header > div {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 18px;
}
.analysis-result__header span {
  color: #777;
  font-size: 12px;
}
.analysis-summary {
  margin: 14px 0;
}
.analysis-summary__card {
  height: 100%;
}
.analysis-summary__title {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.analysis-summary__path {
  overflow: hidden;
  margin-top: 2px;
  color: #999;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.analysis-summary__values {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}
.analysis-summary__values > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}
.analysis-summary__values span {
  color: #888;
  font-size: 11px;
}
.analysis-summary__values strong {
  overflow: hidden;
  color: #222;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.analysis-summary__meta {
  margin-top: 10px;
  color: #999;
  font-size: 11px;
}
.analysis-empty {
  padding: 100px 0;
}

@media (max-width: 900px) {
  .analysis-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .analysis-toolbar__range {
    width: 100%;
  }
  .analysis-result__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
