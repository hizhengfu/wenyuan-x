<template>
  <div class="metric-page">
    <NTabs
      type="line"
      animated
      class="metric-tabs"
      pane-wrapper-style="display: flex; flex: 1; min-height: 0; overflow: hidden;"
      pane-style="display: flex; flex: 1; min-height: 0; flex-direction: column; overflow: hidden;"
    >
      <NTabPane name="preset" tab="预置标准指标" display-directive="show">
        <HPageDataTable
          ref="metricTableRef"
          title="预置标准指标"
          class="metric-data-table"
          :columns="metricColumns"
          :fetch-data="fetchMetrics"
          :pagination="false"
          :scroll-x="1340"
          row-key-field="code"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #queryForm>
            <NFormItem label="关键字">
              <NInput v-model:value="keyword" clearable placeholder="指标名称 / 编码 / 别名" style="width: 280px" />
            </NFormItem>
          </template>
          <template #toolbar-extra>
            <NTag type="success" :bordered="false">已启用 {{ enabledMetricCount }} / {{ metrics.length }} 项</NTag>
          </template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane name="candidate" tab="待确认指标" display-directive="show:lazy">
        <HPageDataTable
          v-if="candidateEnabled"
          ref="candidateTableRef"
          title="待确认指标"
          class="metric-data-table"
          :columns="candidateColumns"
          :fetch-data="fetchCandidates"
          :pagination="false"
          :scroll-x="1180"
          row-key-field="id"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #toolbar-extra>
            <NSpace align="center" :size="8">
              <NSwitch :value="candidateEnabled" :loading="settingsSaving" @update:value="updateCandidateEnabled" />
              <NText>启用待确认指标</NText>
            </NSpace>
          </template>
        </HPageDataTable>
        <div v-else class="candidate-disabled-toolbar">
          <NSwitch :value="candidateEnabled" :loading="settingsSaving" @update:value="updateCandidateEnabled" />
          <NText>启用待确认指标</NText>
        </div>
      </NTabPane>
    </NTabs>
  </div>
</template>

<script setup lang="ts">
import { computed, h, nextTick, reactive, ref } from 'vue';
import {
  NButton,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  NText,
  type DataTableColumns,
  useDialog,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { getDictLabel, useDict } from '@/utils/dict';
import { ingestApi } from '../api/ingest';
import type { MetricCandidateSettingsVo, StandardMetricCandidateVo, StandardMetricDefinition } from '../typings';
import { INGEST_RECORD_TYPE_DICT } from '../utils/ingestDict';

const message = useMessage();
const dialog = useDialog();
const metricTableRef = ref<any>(null);
const candidateTableRef = ref<any>(null);
const settingsSaving = ref(false);
const candidateEnabled = ref(true);
const metricSavingCodes = ref(new Set<string>());
const keyword = ref('');
const metrics = ref<StandardMetricDefinition[]>([]);
const mappings = reactive<Record<string, string | null>>({});
const { options: recordTypeDict } = useDict(INGEST_RECORD_TYPE_DICT);

const metricOptions = computed(() =>
  metrics.value
    .filter((item) => item.enabled)
    .map((item) => ({
      label: `${item.name}（${item.code}）`,
      value: item.code,
    })),
);
const enabledMetricCount = computed(() => metrics.value.filter((item) => item.enabled).length);
const filteredMetrics = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) return metrics.value;
  return metrics.value.filter((item) =>
    [item.name, item.code, item.category, ...(item.aliases || [])].some((text) => text?.toLowerCase().includes(value)),
  );
});

const metricColumns: DataTableColumns<StandardMetricDefinition> = [
  { title: '指标名称', key: 'name', minWidth: 160, fixed: 'left' },
  {
    title: '启用',
    key: 'enabled',
    width: 90,
    render: (row) =>
      h(NSwitch, {
        value: row.enabled,
        loading: metricSavingCodes.value.has(row.code),
        onUpdateValue: (enabled: boolean) => updateStandardMetricEnabled(row, enabled),
      }),
  },
  { title: '稳定编码', key: 'code', minWidth: 230 },
  { title: '分类', key: 'category', width: 140, render: (row) => categoryName(row.category) },
  {
    title: '事实类型',
    key: 'recordType',
    width: 150,
    render: (row) => dictLabel(recordTypeDict.value, row.recordType),
  },
  { title: '值类型', key: 'valueType', width: 100, render: (row) => valueTypeName(row.valueType) },
  {
    title: '数值字段',
    key: 'valueFields',
    minWidth: 220,
    render: (row) => row.valueFields?.length
      ? h(NSpace, { size: 5 }, { default: () => row.valueFields.map((field) =>
          h(NTag, { size: 'small', bordered: false, type: field.required ? 'info' : 'default', title: `${field.name}${field.required ? '（必填）' : ''}` }, { default: () => `${field.code} · ${field.name}` }),
        ) })
      : '--',
  },
  { title: '聚合语义', key: 'aggregation', width: 110, render: (row) => aggregationName(row.aggregation) },
  { title: '单位', key: 'unit', width: 90, render: (row) => row.unit || '--' },
  { title: '说明', key: 'description', minWidth: 260, ellipsis: { tooltip: true } },
  { title: '兼容别名', key: 'aliases', minWidth: 260, render: (row) => row.aliases?.join('、') || '--' },
];

const candidateColumns: DataTableColumns<StandardMetricCandidateVo> = [
  { title: '来源', key: 'sourceCode', minWidth: 170, fixed: 'left' },
  { title: '外部指标键', key: 'externalMetricKey', minWidth: 210 },
  { title: '样例值', key: 'sampleValue', minWidth: 130, render: (row) => row.sampleValue || '--' },
  { title: '出现次数', key: 'seenCount', width: 100 },
  { title: '首次发现', key: 'firstSeenAt', width: 180, render: (row) => formatDateTime(row.firstSeenAt) },
  { title: '最近发现', key: 'lastSeenAt', width: 180, render: (row) => formatDateTime(row.lastSeenAt) },
  {
    title: '确认映射',
    key: 'mapping',
    minWidth: 330,
    fixed: 'right',
    render: (row) =>
      h(
        NSpace,
        { wrap: false, size: 8 },
        {
          default: () => [
            h(NSelect, {
              value: mappings[row.id] || null,
              options: metricOptions.value,
              filterable: true,
              clearable: true,
              placeholder: '选择预置标准指标',
              style: 'width: 230px',
              onUpdateValue: (value: string | null) => {
                mappings[row.id] = value;
              },
            }),
            h(
              NButton,
              { size: 'small', type: 'primary', onClick: () => confirmMapping(row) },
              { default: () => '确认' },
            ),
            h(
              NButton,
              { size: 'small', tertiary: true, type: 'warning', onClick: () => ignoreCandidate(row) },
              { default: () => '忽略' },
            ),
          ],
        },
      ),
  },
];

function responseList<T>(response: any): T[] {
  const data = response?.data?.data ?? response?.data ?? response;
  return Array.isArray(data) ? data : Array.isArray(data?.list) ? data.list : [];
}

function responseData<T>(response: any): T {
  return (response?.data?.data ?? response?.data ?? response) as T;
}

function dictLabel(options: any[], value: string) {
  const label = getDictLabel(options, value);
  return label === '--' ? value : label;
}

async function fetchMetrics() {
  const [metricResponse, settingsResponse] = await Promise.all([
    ingestApi.standardMetrics(true),
    ingestApi.metricCandidateSettings(),
  ]);
  metrics.value = responseList<StandardMetricDefinition>(metricResponse);
  candidateEnabled.value = responseData<MetricCandidateSettingsVo>(settingsResponse)?.enabled !== false;
  return { data: { list: filteredMetrics.value, total: filteredMetrics.value.length, pages: 1 } };
}

async function fetchCandidates() {
  const response = await ingestApi.metricCandidates('PENDING');
  const candidates = responseList<StandardMetricCandidateVo>(response);
  Object.keys(mappings).forEach((key) => {
    delete mappings[key];
  });
  candidates.forEach((row) => {
    mappings[row.id] = row.mappedMetricCode || null;
  });
  return { data: { list: candidates, total: candidates.length, pages: 1 } };
}

async function updateStandardMetricEnabled(row: StandardMetricDefinition, enabled: boolean) {
  if (metricSavingCodes.value.has(row.code) || enabled === row.enabled) return;
  const saving = new Set(metricSavingCodes.value);
  saving.add(row.code);
  metricSavingCodes.value = saving;
  try {
    const response = await ingestApi.updateStandardMetricEnabled(row.code, enabled);
    row.enabled = responseData<StandardMetricDefinition>(response)?.enabled === true;
    message.success(row.enabled ? `已启用 ${row.name}` : `已停用 ${row.name}`);
  } finally {
    const next = new Set(metricSavingCodes.value);
    next.delete(row.code);
    metricSavingCodes.value = next;
  }
}

async function updateCandidateEnabled(enabled: boolean) {
  if (settingsSaving.value || enabled === candidateEnabled.value) return;
  settingsSaving.value = true;
  try {
    const response = await ingestApi.updateMetricCandidateSettings(enabled);
    candidateEnabled.value = responseData<MetricCandidateSettingsVo>(response)?.enabled === true;
    if (candidateEnabled.value) {
      await nextTick();
      candidateTableRef.value?.handleRefresh?.();
    }
    message.success(candidateEnabled.value ? '已启用待确认指标' : '已关闭，未知指标将直接丢弃');
  } finally {
    settingsSaving.value = false;
  }
}

async function confirmMapping(row: StandardMetricCandidateVo) {
  const metricCode = mappings[row.id];
  if (!metricCode) {
    message.warning('请先选择预置标准指标');
    return;
  }
  await ingestApi.mapMetricCandidate(row.id, metricCode);
  message.success('指标映射已确认，后续同来源指标会自动归一化');
  candidateTableRef.value?.handleRefresh?.();
}

function ignoreCandidate(row: StandardMetricCandidateVo) {
  dialog.warning({
    title: '忽略待确认指标',
    content: `确认忽略 ${row.sourceCode} / ${row.externalMetricKey}？后续数据仍不会自动入库。`,
    positiveText: '确认忽略',
    negativeText: '取消',
    onPositiveClick: async () => {
      await ingestApi.ignoreMetricCandidate(row.id);
      message.success('已忽略该指标');
      candidateTableRef.value?.handleRefresh?.();
    },
  });
}

function categoryName(value: string) {
  return (
    (
      {
        VISITOR_FLOW: '客流',
        DEVICE_HEALTH: '设备健康',
        ENVIRONMENT: '环境',
        POLLUTION: '污染物',
        VIBRATION: '振动',
        BIOLOGY: '生物病害',
        STRUCTURE: '建筑结构',
        FIRE: '消防',
        FIRE_SAFETY: '消防',
        SECURITY: '安防',
        LIGHTNING: '防雷',
        LIGHTNING_PROTECTION: '防雷',
        ELECTRICITY: '用电',
        BUILDING_ENVIRONMENT: '建筑环境',
      } as Record<string, string>
    )[value] || value
  );
}
function valueTypeName(value: string) {
  return ({ NUMBER: '数值', STRING: '文本', BOOLEAN: '布尔' } as Record<string, string>)[value] || value;
}
function aggregationName(value: string) {
  return (
    ({ GAUGE: '瞬时值', INCREMENT: '窗口增量', CUMULATIVE: '累计值', STATE: '状态' } as Record<string, string>)[
      value
    ] || value
  );
}
</script>

<style scoped>
.metric-page,
.metric-tabs {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.metric-tabs :deep(.n-tabs-nav) {
  flex: none;
}

.metric-tabs :deep(.n-tabs-pane-wrapper),
.metric-tabs :deep(.n-tab-pane) {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.metric-data-table {
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
}

.candidate-disabled-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
}
</style>
