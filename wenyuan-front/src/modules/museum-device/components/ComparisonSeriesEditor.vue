<template>
  <div class="comparison-series-editor">
    <div class="comparison-series-editor__header">
      <div>
        <div class="comparison-series-editor__title">对比序列</div>
        <div class="comparison-series-editor__hint">每条序列由一个设备和该设备在当前时段内实际出现的数值指标组成</div>
      </div>
      <NButton size="small" :disabled="modelValue.length >= MAX_SERIES" @click="addSeries">添加序列</NButton>
    </div>

    <div class="comparison-series-editor__rows">
      <div v-for="(row, index) in modelValue" :key="row.seriesId" class="comparison-series-editor__row">
        <NTag size="small" :bordered="false" class="comparison-series-editor__tag">{{ row.seriesId }}</NTag>
        <NSelect
          :value="row.deviceId"
          :options="deviceOptions"
          :filter="filterOption"
          filterable
          clearable
          placeholder="选择设备"
          class="comparison-series-editor__device"
          @update:value="(value) => changeDevice(index, value)"
        />
        <NSelect
          :value="row.key"
          :options="metricOptions(index)"
          :loading="Boolean(row.deviceId && loadingDevices.has(row.deviceId))"
          :disabled="!row.deviceId"
          filterable
          clearable
          placeholder="选择该设备的数值指标"
          class="comparison-series-editor__metric"
          @update:value="(value) => changeMetric(index, value)"
        />
        <NSelect
          :value="row.fieldCode"
          :options="fieldOptions(index)"
          :disabled="!row.key"
          clearable
          placeholder="选择字段"
          class="comparison-series-editor__field"
          @update:value="(value) => patchSeries(index, { fieldCode: value })"
        />
        <NButton
          quaternary
          type="error"
          size="small"
          :disabled="modelValue.length <= MIN_SERIES"
          @click="removeSeries(index)"
        >
          移除
        </NButton>
      </div>
    </div>
    <div class="comparison-series-editor__footer">
      已添加 {{ modelValue.length }} / {{ MAX_SERIES }} 条序列；同一设备、指标和字段不能重复添加
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, watch } from 'vue';
import { NButton, NSelect, NTag, type SelectOption } from 'naive-ui';
import { ingestApi } from '../api/ingest';
import type {
  MuseumAnalysisMetricOptionVo,
  MuseumAnalysisMetricOptionsVo,
  MuseumComparisonSeriesDraft,
  RecordFilterDeviceVo,
} from '../typings';

const MIN_SERIES = 2;
const MAX_SERIES = 8;

const props = defineProps<{
  modelValue: MuseumComparisonSeriesDraft[];
  devices: RecordFilterDeviceVo[];
  from: number;
  to: number;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: MuseumComparisonSeriesDraft[]];
}>();

interface SearchableOption extends SelectOption {
  searchText: string;
}

const metricCatalog = reactive<Record<string, MuseumAnalysisMetricOptionVo[]>>({});
const loadingDevices = reactive(new Set<string>());
const requestVersions = new Map<string, number>();
let nextSeriesNumber = Math.max(
  MIN_SERIES,
  ...props.modelValue.map((item) => Number(item.seriesId.replace(/\D/g, '')) || 0),
);

const deviceOptions = computed<SearchableOption[]>(() =>
  props.devices.map((device) => ({
    label: `${device.name || device.code}（${device.code}）${device.locationPath ? ` · ${device.locationPath}` : ''}${
      device.status === 'NORMAL' ? '' : ' [已停用]'
    }`,
    value: device.id,
    searchText: [device.id, device.code, device.name, device.externalDeviceId, device.locationPath]
      .filter(Boolean)
      .join(' '),
  })),
);

function unwrapResponse<T>(response: any): T | null {
  let payload = response;
  for (let depth = 0; depth < 5; depth += 1) {
    if (payload === null || typeof payload !== 'object' || !('data' in payload)) return payload as T;
    payload = payload.data;
  }
  return payload as T;
}

async function loadMetricOptions(deviceId: string) {
  if (!deviceId || !props.from || !props.to || props.from >= props.to) return;
  const version = (requestVersions.get(deviceId) || 0) + 1;
  requestVersions.set(deviceId, version);
  loadingDevices.add(deviceId);
  try {
    const payload = unwrapResponse<MuseumAnalysisMetricOptionsVo>(
      await ingestApi.analysisMetricOptions({ deviceId, from: props.from, to: props.to }),
    );
    if (requestVersions.get(deviceId) !== version) return;
    metricCatalog[deviceId] = Array.isArray(payload?.metrics) ? payload.metrics : [];
    reconcileSelectedMetrics(deviceId);
  } catch {
    if (requestVersions.get(deviceId) === version) metricCatalog[deviceId] = [];
  } finally {
    if (requestVersions.get(deviceId) === version) loadingDevices.delete(deviceId);
  }
}

function reconcileSelectedMetrics(deviceId: string) {
  let changed = false;
  const next = props.modelValue.map((row) => {
    if (row.deviceId !== deviceId || !row.key) return row;
    const metric = (metricCatalog[deviceId] || []).find((item) => item.code === row.key);
    if (!metric) { changed = true; return { ...row, key: null, fieldCode: null }; }
    const fields = metric.valueFields || [];
    if (row.fieldCode && fields.some((field) => field.code === row.fieldCode)) return row;
    changed = true;
    return { ...row, fieldCode: fields.length === 1 ? fields[0].code : null };
  });
  if (changed) emit('update:modelValue', next);
}

function metricOptions(index: number): SelectOption[] {
  const row = props.modelValue[index];
  if (!row?.deviceId) return [];
  return (metricCatalog[row.deviceId] || []).map((metric) => ({
    label: `${metric.name || metric.code}（${metric.code}）${metric.unit ? ` · ${metric.unit}` : ''}${
      metric.enabled ? '' : ' [已停用]'
    }`,
    value: metric.code,
  }));
}

function fieldOptions(index: number): SelectOption[] {
  const row = props.modelValue[index];
  if (!row?.deviceId || !row.key) return [];
  const metric = (metricCatalog[row.deviceId] || []).find((item) => item.code === row.key);
  return (metric?.valueFields || []).map((field) => ({ label: `${field.name}（${field.code}）`, value: field.code }));
}

function patchSeries(index: number, patch: Partial<MuseumComparisonSeriesDraft>) {
  emit(
    'update:modelValue',
    props.modelValue.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)),
  );
}

function changeDevice(index: number, value: string | null) {
  patchSeries(index, { deviceId: value, key: null, fieldCode: null });
  if (value) void loadMetricOptions(value);
}

function changeMetric(index: number, value: string | null) {
  const row = props.modelValue[index];
  const fields = row?.deviceId && value
    ? (metricCatalog[row.deviceId] || []).find((item) => item.code === value)?.valueFields || []
    : [];
  patchSeries(index, { key: value, fieldCode: fields.length === 1 ? fields[0].code : null });
}

function addSeries() {
  if (props.modelValue.length >= MAX_SERIES) return;
  nextSeriesNumber += 1;
  emit('update:modelValue', [...props.modelValue, { seriesId: `S${nextSeriesNumber}`, deviceId: null, key: null, fieldCode: null }]);
}

function removeSeries(index: number) {
  if (props.modelValue.length <= MIN_SERIES) return;
  emit(
    'update:modelValue',
    props.modelValue.filter((_, rowIndex) => rowIndex !== index),
  );
}

function filterOption(pattern: string, option: SelectOption): boolean {
  const searchable = option as SearchableOption;
  return `${String(searchable.label || '')} ${searchable.searchText || ''}`
    .toLowerCase()
    .includes(pattern.trim().toLowerCase());
}

async function reloadAllMetrics() {
  Object.keys(metricCatalog).forEach((key) => delete metricCatalog[key]);
  const deviceIds = [...new Set(props.modelValue.map((item) => item.deviceId).filter(Boolean))] as string[];
  await Promise.all(deviceIds.map(loadMetricOptions));
}

watch(
  () => [props.from, props.to],
  () => void reloadAllMetrics(),
);
watch(
  () => props.modelValue.map((item) => item.deviceId).join('|'),
  () => {
    props.modelValue.forEach((item) => {
      if (item.deviceId && !metricCatalog[item.deviceId] && !loadingDevices.has(item.deviceId)) {
        void loadMetricOptions(item.deviceId);
      }
    });
  },
);
onMounted(() => void reloadAllMetrics());

defineExpose({ reloadAllMetrics });
</script>

<style scoped>
.comparison-series-editor {
  padding: 14px;
  border: 1px solid #e8ebef;
  border-radius: 8px;
  background: #fafbfc;
}
.comparison-series-editor__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.comparison-series-editor__title {
  color: #222;
  font-size: 14px;
  font-weight: 600;
}
.comparison-series-editor__hint,
.comparison-series-editor__footer {
  margin-top: 3px;
  color: #888;
  font-size: 12px;
}
.comparison-series-editor__rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.comparison-series-editor__row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.comparison-series-editor__tag {
  width: 42px;
  justify-content: center;
}
.comparison-series-editor__device {
  width: min(360px, 38%);
}
.comparison-series-editor__metric {
  width: min(360px, 38%);
}
.comparison-series-editor__field {
  width: 160px;
}
.comparison-series-editor__footer {
  margin-top: 10px;
}

@media (max-width: 900px) {
  .comparison-series-editor__row {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .comparison-series-editor__device,
  .comparison-series-editor__metric {
    width: calc(50% - 32px);
    min-width: 240px;
    flex: 1;
  }
  .comparison-series-editor__field { min-width: 180px; flex: 1; }
}
</style>
