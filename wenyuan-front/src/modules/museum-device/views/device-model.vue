<template>
  <HPageDataTable
    :columns="columns"
    :fetch-data="fetchData"
    :params="queryParams"
    row-key-field="code"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :show-operate="false"
    @row-view="openView"
  >
    <template #queryForm>
      <NFormItem label="厂商">
        <NSelect v-model:value="queryParams.vendor" :options="vendorOptions" clearable filterable style="width: 180px" />
      </NFormItem>
      <NFormItem label="关键字">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="型号名称 / 编码 / 指标" style="width: 260px" />
      </NFormItem>
    </template>

    <HPageView v-model:show="viewShow" title="设备型号详情" width="760px" :show-action="false">
      <NDescriptions v-if="viewRow" label-placement="left" :column="2" bordered size="small">
        <NDescriptionsItem label="型号名称">{{ viewRow.name }}</NDescriptionsItem>
        <NDescriptionsItem label="型号编码">{{ viewRow.code }}</NDescriptionsItem>
        <NDescriptionsItem label="厂商">{{ viewRow.vendor }}</NDescriptionsItem>
        <NDescriptionsItem label="定义版本">{{ viewRow.version }}</NDescriptionsItem>
        <NDescriptionsItem label="设备 Codec" :span="2">{{ viewRow.codecCode }}</NDescriptionsItem>
        <NDescriptionsItem label="兼容协议" :span="2">{{ protocolText(viewRow.protocolCodes) }}</NDescriptionsItem>
        <NDescriptionsItem label="指标模式" :span="2">{{ viewRow.dynamicMetrics ? '动态指标型号' : '固定指标型号' }}</NDescriptionsItem>
      </NDescriptions>
      <NDataTable
        v-if="viewRow"
        :columns="metricColumns"
        :data="viewRow.metrics || []"
        :pagination="false"
        :bordered="false"
        size="small"
        style="margin-top: 16px"
      />
    </HPageView>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue';
import { NDataTable, NDescriptions, NDescriptionsItem, NFormItem, NInput, NSelect, NTag, type DataTableColumns } from 'naive-ui';
import { ingestConfigApi } from '../api/ingestConfig';
import type { IngestCatalog, IngestDeviceModelDefinition, IngestDeviceModelMetricDefinition } from '../typings';

const catalog = ref<IngestCatalog>({ protocols: [], codecs: [], deviceModels: [], builtInConnectors: [], standardMetrics: [] });
const queryParams = reactive({ vendor: null as string | null, keyword: null as string | null });
const viewShow = ref(false);
const viewRow = ref<IngestDeviceModelDefinition | null>(null);

const vendorOptions = computed(() => [...new Set(catalog.value.deviceModels.map((item) => item.vendor))]
  .sort((a, b) => a.localeCompare(b, 'zh-CN'))
  .map((value) => ({ label: value, value })));

const columns = [
  { title: '型号名称', key: 'name', minWidth: 190, columnType: 'title', ellipsis: { tooltip: true } },
  { title: '型号编码', key: 'code', minWidth: 250, ellipsis: { tooltip: true } },
  { title: '厂商', key: 'vendor', width: 140 },
  { title: '版本', key: 'version', width: 90 },
  { title: '设备 Codec', key: 'codecCode', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '支持指标', key: 'metrics', minWidth: 260, render: (row: IngestDeviceModelDefinition) => metricTags(row) },
  { title: '指标模式', key: 'dynamicMetrics', width: 110, render: (row: IngestDeviceModelDefinition) => row.dynamicMetrics ? '动态' : '固定' },
];

const metricColumns: DataTableColumns<IngestDeviceModelMetricDefinition> = [
  { title: '设备输出键', key: 'externalMetricKey', minWidth: 180 },
  { title: '指标名称', key: 'name', minWidth: 150 },
  { title: '统一指标编码', key: 'metricCode', minWidth: 150 },
  { title: '单位', key: 'unit', width: 90, render: (row) => row.unit || '--' },
  { title: '默认换算', key: 'scale', width: 150, render: (row) => `× ${row.scale ?? 1} + ${row.offset ?? 0}` },
];

function metricTags(row: IngestDeviceModelDefinition) {
  const metrics = row.metrics || [];
  if (!metrics.length) return row.dynamicMetrics ? '运行时动态指标' : '--';
  return h('div', { style: 'display:flex;gap:6px;flex-wrap:wrap' }, metrics.map((metric) =>
    h(NTag, { size: 'small', bordered: false }, { default: () => `${metric.name}（${metric.metricCode}）` }),
  ));
}

function protocolText(codes: string[]) {
  return codes.map((code) => catalog.value.protocols.find((item) => item.code === code)?.name || code).join('、') || '--';
}

async function loadCatalog() {
  const response: any = await ingestConfigApi.catalog();
  catalog.value = response?.data || { protocols: [], codecs: [], deviceModels: [], builtInConnectors: [], standardMetrics: [] };
}

async function fetchData(params: any) {
  await loadCatalog();
  const keyword = queryParams.keyword?.trim().toLowerCase();
  const rows = catalog.value.deviceModels.filter((item) => {
    if (queryParams.vendor && item.vendor !== queryParams.vendor) return false;
    if (!keyword) return true;
    return [item.name, item.code, item.vendor, item.codecCode, ...(item.metrics || []).flatMap((metric) => [metric.name, metric.metricCode, metric.externalMetricKey])]
      .some((value) => value?.toLowerCase().includes(keyword));
  });
  const pageSize = Number(params.pageSize || 20);
  const pageNum = Number(params.pageNum || 1);
  return { data: { list: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize), total: rows.length } };
}

function openView(row: IngestDeviceModelDefinition) {
  viewRow.value = row;
  viewShow.value = true;
}

onMounted(loadCatalog);
</script>
