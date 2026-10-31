<template>
  <div class="records-page">
    <HPageDataTable
      ref="tableRef"
      title="采集数据"
      :columns="columns"
      :fetch-data="fetchRecords"
      :params="query"
      :pagination="{ showQuickJumper: mode === 'latest' }"
      :reset-page-on-size-change="mode === 'history'"
      :scroll-x="2020"
      row-key-field="id"
      :is-add="false"
      :is-edit="false"
      :is-copy="false"
      :is-delete="false"
      :is-export="false"
      :show-operate="false"
    >
      <template #queryForm>
        <NFormItem label="设备">
          <NSelect
            v-model:value="query.deviceId"
            :options="deviceOptions"
            :filter="filterCatalogOption"
            :loading="filterOptionsLoading"
            clearable
            filterable
            placeholder="全部设备"
            style="width: 300px"
          />
        </NFormItem>
        <NFormItem label="设备位置">
          <NSelect
            v-model:value="query.locationId"
            :options="locationOptions"
            :filter="filterCatalogOption"
            :loading="filterOptionsLoading"
            clearable
            filterable
            placeholder="全部位置"
            style="width: 300px"
          />
        </NFormItem>
        <NFormItem v-if="mode === 'history'" label="网关">
          <NSelect
            v-model:value="query.gatewayId"
            :options="gatewayOptions"
            :loading="gatewayOptionsLoading"
            clearable
            filterable
            placeholder="全部网关"
            style="width: 260px"
          />
        </NFormItem>
        <NFormItem v-if="mode === 'history'" label="数据类型">
          <NSelect
            v-model:value="query.recordType"
            :options="recordTypeDict"
            clearable
            placeholder="全部类型"
            style="width: 170px"
          />
        </NFormItem>
        <NFormItem label="指标名称">
          <NSelect
            v-model:value="query.key"
            :options="metricOptions"
            clearable
            filterable
            placeholder="全部指标/状态"
            style="width: 240px"
          />
        </NFormItem>
        <NFormItem v-if="metricFieldOptions.length" label="数值字段">
          <NSelect
            v-model:value="query.fieldCode"
            :options="metricFieldOptions"
            clearable
            :placeholder="metricFieldOptions.length === 1 ? '监测值' : '请选择字段'"
            style="width: 150px"
          />
        </NFormItem>
        <NFormItem label="数值条件">
          <NSelect
            v-model:value="query.valueOperator"
            :options="valueOperatorOptions"
            clearable
            placeholder="不限"
            style="width: 150px"
            @update:value="changeValueOperator"
          />
        </NFormItem>
        <NFormItem v-if="query.valueOperator" :label="query.valueOperator === 'BETWEEN' ? '区间下限' : '比较值'">
          <NInputNumber v-model:value="query.value" clearable style="width: 140px" />
        </NFormItem>
        <NFormItem v-if="query.valueOperator === 'BETWEEN'" label="区间上限">
          <NInputNumber v-model:value="query.value2" clearable style="width: 140px" />
        </NFormItem>
        <NFormItem v-if="mode === 'history'" label="数据质量">
          <NSelect
            v-model:value="query.quality"
            :options="qualityDict"
            clearable
            placeholder="全部质量"
            style="width: 150px"
          />
        </NFormItem>
        <NFormItem v-if="mode === 'history'" label="是否回灌">
          <NSelect
            v-model:value="query.backfill"
            :options="backfillOptions"
            clearable
            placeholder="全部"
            style="width: 120px"
          />
        </NFormItem>
        <NFormItem v-if="mode === 'history'" label="发生时间">
          <NDatePicker v-model:value="query.range" type="datetimerange" clearable style="width: 350px" />
        </NFormItem>
      </template>
      <template #toolbar-extra>
        <NRadioGroup v-model:value="mode" size="small" @update:value="changeMode">
          <NRadioButton value="latest">实时数据</NRadioButton>
          <NRadioButton value="history">历史记录</NRadioButton>
        </NRadioGroup>
      </template>
    </HPageDataTable>

    <NModal
      v-model:show="historyVisible"
      preset="card"
      :title="historyTitle"
      style="width: 60%"
      content-style="max-height: min(82vh, 860px); overflow: auto;"
    >
      <DeviceMetricHistory
        v-if="historyContext"
        :key="historyComponentKey"
        :device-id="historyContext.record.deviceId"
        :metrics="historyMetrics"
        :default-metric-key="historyContext.record.key"
        :default-range="historyContext.range"
      />
    </NModal>
    <IngestRecordDetails v-model:show="detailsVisible" :record="detailsRecord" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import {
  NButton,
  NDatePicker,
  NFormItem,
  NInputNumber,
  NModal,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NTag,
  NTooltip,
  type DataTableColumns,
  type SelectOption,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { getDictLabel, useDict } from '@/utils/dict';
import { gatewayApi } from '../api/gateway';
import { ingestApi } from '../api/ingest';
import type {
  AlarmOperator,
  GatewayVo,
  IngestDataQuality,
  IngestRecordType,
  IngestRecordVo,
  MuseumSpatialMetricVo,
  RecordFilterOptionsVo,
  StandardMetricDefinition,
} from '../typings';
import { INGEST_DATA_QUALITY_DICT, INGEST_RECORD_TYPE_DICT } from '../utils/ingestDict';
import { formatMetricValue } from '../utils/metricValue';
import DeviceMetricHistory from '../components/DeviceMetricHistory.vue';
import IngestRecordDetails from '../components/IngestRecordDetails.vue';
import { recordSummary, recordTarget } from '../utils/recordDisplay';
import { metricDisplayName } from '../utils/metricName';
import { HistoryCursorPager } from '../utils/historyCursorPager';

const route = useRoute();
const tableRef = ref<any>(null);
const mode = ref<'latest' | 'history'>('latest');
const historyVisible = ref(false);
const detailsVisible = ref(false);
const detailsRecord = ref<IngestRecordVo | null>(null);
const historyContext = ref<{ record: IngestRecordVo; range: [number, number] } | null>(null);
const metrics = ref<StandardMetricDefinition[]>([]);
const gateways = ref<GatewayVo[]>([]);
const gatewayOptionsLoading = ref(false);
const gatewayOptions = computed(() =>
  gateways.value
    .filter((gateway) => gateway.id)
    .map((gateway) => ({
      label: gateway.code ? `${gateway.name || gateway.code}（${gateway.code}）` : gateway.name || gateway.id!,
      value: gateway.id!,
    })),
);
const gatewayMap = computed(
  () => new Map(gateways.value.filter((gateway) => gateway.id).map((gateway) => [gateway.id!, gateway])),
);
const metricOptions = computed(() =>
  metrics.value.map((metric) => ({
    label: `${metric.name}（${metric.code}）${metric.enabled ? '' : ' [已停用]'}`,
    value: metric.code,
  })),
);
const filterOptions = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const filterOptionsLoading = ref(false);
const deviceOptions = computed<SearchableOption[]>(() =>
  filterOptions.value.devices.map((device) => ({
    label: `${device.name || device.code}（${device.code}）${device.locationPath ? ` · ${device.locationPath}` : ''}${
      device.status === 'NORMAL' ? '' : ' [已停用]'
    }`,
    value: device.id,
    searchText: [device.id, device.code, device.name, device.externalDeviceId, device.locationPath]
      .filter(Boolean)
      .join(' '),
  })),
);
const locationOptions = computed<SearchableOption[]>(() =>
  filterOptions.value.locations.map((location) => ({
    label: `${location.path || location.name}（${location.code}）${location.status === 'NORMAL' ? '' : ' [已停用]'}`,
    value: location.id,
    searchText: [location.id, location.code, location.name, location.path].filter(Boolean).join(' '),
  })),
);
const defaultHistoryTo = Date.now();
const query = reactive({
  deviceId: (route.query.deviceId as string) || null,
  locationId: null as string | null,
  gatewayId: null as string | null,
  recordType: null as IngestRecordType | null,
  key: (route.query.key as string) || null,
  fieldCode: null as string | null,
  quality: null as IngestDataQuality | null,
  backfill: null as 'true' | 'false' | null,
  valueOperator: null as Extract<AlarmOperator, 'GT' | 'LT' | 'BETWEEN'> | null,
  value: null as number | null,
  value2: null as number | null,
  range: [defaultHistoryTo - 24 * 60 * 60 * 1000, defaultHistoryTo] as [number, number] | null,
});
const selectedMetric = computed(() => metrics.value.find((item) => item.code === query.key));
const historyMetrics = computed<MuseumSpatialMetricVo[]>(() => {
  const row = historyContext.value?.record;
  if (!row) return [];
  return [
    {
      key: row.key,
      name: metricDisplayName(row.key, row.metricName, row.sourceMetricName),
      numericValue: recordNumericValue(row),
      stringValue: row.stringValue,
      booleanValue: row.booleanValue,
      unit: recordUnit(row) || null,
      occurredAt: row.occurredAt,
      receivedAt: row.receivedAt,
    },
  ];
});
const historyTitle = computed(() => {
  const row = historyContext.value?.record;
  if (!row) return '历史数据';
  const suffix = canShowMetricCurve(row) ? '历史曲线' : '历史数据';
  return `${row.deviceName || row.deviceId} · ${metricDisplayName(row.key, row.metricName, row.sourceMetricName)}${suffix}`;
});
const historyComponentKey = computed(() => {
  if (!historyContext.value) return 'record-history';
  const { record, range } = historyContext.value;
  return `${record.deviceId}:${record.key}:${range[0]}:${range[1]}`;
});
const metricFieldOptions = computed(() =>
  (selectedMetric.value?.valueFields || []).map((field) => ({ label: field.name, value: field.code })),
);
const { options: recordTypeDict } = useDict(INGEST_RECORD_TYPE_DICT);
const { options: qualityDict } = useDict(INGEST_DATA_QUALITY_DICT);
const qualityTypes: Record<string, 'success' | 'warning' | 'error'> = {
  NORMAL: 'success',
  SUSPECT: 'warning',
  INVALID: 'error',
};
const backfillOptions = [
  { label: '是', value: 'true' },
  { label: '否', value: 'false' },
];
const valueOperatorOptions = [
  { label: '大于（>）', value: 'GT' },
  { label: '小于（<）', value: 'LT' },
  { label: '区间内（含边界）', value: 'BETWEEN' },
];
watch(
  () => query.key,
  () => {
    query.fieldCode = metricFieldOptions.value.length === 1 ? String(metricFieldOptions.value[0].value) : null;
  },
);
interface SearchableOption extends SelectOption {
  value: string;
  searchText: string;
}
const columns: DataTableColumns<IngestRecordVo> = [
  { title: '发生时间', key: 'occurredAt', width: 185, fixed: 'left', render: (row) => formatDateTime(row.occurredAt) },
  { title: '设备', key: 'deviceName', minWidth: 190, fixed: 'left', render: (row) => row.deviceName || row.deviceId },
  {
    title: '位置',
    key: 'locationPath',
    minWidth: 220,
    render: (row) => row.locationPath || row.locationName || row.locationId || '--',
  },
  { title: '数据类型', key: 'recordType', width: 120, render: (row) => recordTypeLabel(row.recordType) },
  { title: '指标名称', key: 'metricName', minWidth: 190, render: (row) => metricDisplayName(row.key, row.metricName, row.sourceMetricName) },
  { title: '值 / 事件摘要', key: 'value', minWidth: 250, render: (row) => displayValue(row) },
  { title: '对应目标', key: 'target', minWidth: 240, render: (row) => recordTarget(row) || '--' },
  {
    title: '网关',
    key: 'gatewayName',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (row) => gatewayDisplayName(row),
  },
  {
    title: columnHelpTitle('质量', [
      '正常：数据可信，参与最新值、趋势统计和告警计算。',
      '可疑：能正常解析，但可信度存疑；当前仍参与最新值、趋势统计和告警计算。',
      '无效：仅保留历史追溯，不更新最新值、不进入趋势统计；仍可被数据质量告警识别。',
    ]),
    key: 'quality',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: qualityTypes[row.quality] || 'default', bordered: false },
        { default: () => qualityLabel(row.quality) },
      ),
  },
  {
    title: columnHelpTitle('回灌', [
      '是：历史数据同步、设备补传或重放得到的数据，不是当前实时上报。',
      '否：实时或常规周期采集得到的数据。',
      '回灌与数据质量相互独立；回灌数据也可以是“正常”，是否触发规则由告警配置中的“历史回灌触发”决定。',
    ]),
    key: 'backfill',
    width: 80,
    render: (row) => (row.backfill ? '是' : '否'),
  },
  { title: '接收时间', key: 'receivedAt', width: 185, render: (row) => formatDateTime(row.receivedAt) },
  { title: '详情', key: 'details', width: 80, fixed: 'right', render: (row) => h(NButton, {
    text: true, type: 'primary', onClick: () => { detailsRecord.value = row; detailsVisible.value = true; },
  }, { default: () => '查看详情' }) },
];

function columnHelpTitle(title: string, lines: string[]) {
  return () =>
    h(
      NTooltip,
      { placement: 'top-start', trigger: 'hover' },
      {
        trigger: () =>
          h(
            'span',
            {
              style:
                'display:inline-flex;align-items:center;gap:4px;cursor:help;border-bottom:1px dotted var(--n-th-text-color);',
            },
            [title, h('span', { style: 'font-size:12px;opacity:.7;' }, 'ⓘ')],
          ),
        default: () =>
          h(
            'div',
            { style: 'max-width:420px;white-space:normal;line-height:1.65;' },
            lines.map((line) => h('div', { style: 'margin:2px 0;' }, line)),
          ),
      },
    );
}

function recordTypeLabel(value: string) {
  return dictLabel(recordTypeDict.value, value);
}

function qualityLabel(value: string) {
  return dictLabel(qualityDict.value, value);
}

function dictLabel(options: any[], value: string) {
  const label = getDictLabel(options, value);
  return label === '--' ? value : label;
}

function displayValue(row: IngestRecordVo) {
  const content = recordValueContent(row);
  if (!canShowHistory(row)) return content;
  return h(
    NTooltip,
    { placement: 'top', trigger: 'hover' },
    {
      trigger: () =>
        h(
          'div',
          {
            class: 'history-value-link',
            role: 'button',
            tabindex: 0,
            onClick: () => openHistory(row),
            onKeydown: (event: KeyboardEvent) => openHistoryFromKeyboard(event, row),
          },
          [content],
        ),
      default: () => (canShowMetricCurve(row) ? '点击查看历史曲线' : '点击查看历史数据'),
    },
  );
}

function recordValueContent(row: IngestRecordVo) {
  const values = Object.entries(row.values || {});
  const unit = recordUnit(row);
  if (values.length) {
    const metric = metrics.value.find((item) => item.code === row.key);
    return h(
      NSpace,
      { size: 5 },
      {
        default: () =>
          values.map(([code, value]) => {
            const name = metric?.valueFields.find((field) => field.code === code)?.name || code;
            return h(
              NTag,
              { size: 'small', bordered: false, type: 'info' },
              { default: () => `${name} ${formatMetricValue({ numericValue: value, unit })}` },
            );
          }),
      },
    );
  }
  return recordSummary(row);
}

function recordUnit(row: IngestRecordVo) {
  return row.metricUnit || row.unit || '';
}

function gatewayDisplayName(row: IngestRecordVo) {
  const gateway = row.gatewayId ? gatewayMap.value.get(row.gatewayId) : null;
  return row.gatewayName || gateway?.name || gateway?.code || row.gatewayId || '--';
}

function recordNumericValue(row: IngestRecordVo): number | null {
  if (row.numericValue !== null && row.numericValue !== undefined && Number.isFinite(Number(row.numericValue))) {
    return Number(row.numericValue);
  }
  const value = Object.values(row.values || {}).find((item) => Number.isFinite(Number(item)));
  return value === undefined ? null : Number(value);
}

function canShowHistory(row: IngestRecordVo) {
  return (
    canShowMetricCurve(row) ||
    (row.booleanValue !== null && row.booleanValue !== undefined) ||
    (row.stringValue !== null && row.stringValue !== undefined)
  );
}

function canShowMetricCurve(row: IngestRecordVo) {
  return recordNumericValue(row) !== null;
}

function openHistory(row: IngestRecordVo) {
  const rowTime = Number(row.occurredAt);
  const range =
    mode.value === 'history' && query.range ? ([...query.range] as [number, number]) : defaultRangeAround(rowTime);
  historyContext.value = { record: row, range };
  historyVisible.value = true;
}

function openHistoryFromKeyboard(event: KeyboardEvent, row: IngestRecordVo) {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  openHistory(row);
}

function defaultRangeAround(timestamp: number): [number, number] {
  const halfDay = 12 * 60 * 60 * 1000;
  const to = Number.isFinite(timestamp) ? Math.min(Date.now(), timestamp + halfDay) : Date.now();
  return [to - 2 * halfDay, to];
}

function changeValueOperator(operator: Extract<AlarmOperator, 'GT' | 'LT' | 'BETWEEN'> | null) {
  query.valueOperator = operator;
  if (!operator) {
    query.value = null;
    query.value2 = null;
    return;
  }
  if (query.value == null) query.value = 0;
  if (operator === 'BETWEEN') {
    if (query.value2 == null) query.value2 = query.value;
  } else {
    query.value2 = null;
  }
}

function responseList<T>(response: any): T[] {
  const data = response?.data;
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.list)) return data.list;
  return [];
}

async function loadMetricOptions() {
  try {
    const definitions = responseList<StandardMetricDefinition>(await ingestApi.standardMetrics(true));
    metrics.value = definitions.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
  } catch {
    metrics.value = [];
  }
}

async function loadRecordFilterOptions() {
  filterOptionsLoading.value = true;
  try {
    const response: any = await ingestApi.recordFilterOptions();
    filterOptions.value = {
      devices: Array.isArray(response?.data?.devices) ? response.data.devices : [],
      locations: Array.isArray(response?.data?.locations) ? response.data.locations : [],
    };
  } catch {
    filterOptions.value = { devices: [], locations: [] };
  } finally {
    filterOptionsLoading.value = false;
  }
}

async function loadGatewayOptions() {
  gatewayOptionsLoading.value = true;
  try {
    gateways.value = responseList<GatewayVo>(await gatewayApi.page({ pageNum: 1, pageSize: 0 }));
  } catch {
    gateways.value = [];
  } finally {
    gatewayOptionsLoading.value = false;
  }
}

function filterCatalogOption(pattern: string, option: SelectOption): boolean {
  const searchable = option as SearchableOption;
  return `${String(searchable.label || '')} ${searchable.searchText}`
    .toLowerCase()
    .includes(pattern.trim().toLowerCase());
}

const historyPager = new HistoryCursorPager<IngestRecordVo>();

function fetchRecords(params: Record<string, unknown>) {
  const { range, gatewayId, recordType, quality, backfill, pageNum, pageSize, ...common } = params as Record<
    string,
    unknown
  > & {
    range?: [number, number] | null;
    gatewayId?: string | null;
    recordType?: IngestRecordType | null;
    quality?: IngestDataQuality | null;
    backfill?: 'true' | 'false' | null;
    pageNum?: number;
    pageSize?: number;
  };
  if (mode.value === 'latest') {
    return ingestApi.latestRecords({ ...common, pageNum, pageSize, locationId: query.locationId || undefined });
  }
  return historyPager.fetch(
    { pageNum, pageSize },
    { ...common, gatewayId, recordType, quality, backfill, from: range?.[0], to: range?.[1] },
    async (requestParams) => {
      const response: any = await ingestApi.recordWindow(requestParams);
      return response?.data?.data ?? response?.data ?? response;
    },
  );
}

function changeMode() {
  historyPager.reset();
  tableRef.value?.handleQuery?.();
}

onMounted(() => {
  void loadMetricOptions();
  void loadRecordFilterOptions();
  void loadGatewayOptions();
});
</script>

<style scoped>
.records-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.history-value-link {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  max-width: 100%;
  color: var(--primary-color, #18a058);
  cursor: pointer;
  white-space: normal;
}

.history-value-link:hover {
  text-decoration: underline;
}

.history-value-link:focus-visible {
  border-radius: 3px;
  outline: 2px solid var(--primary-color, #18a058);
  outline-offset: 2px;
}
</style>
