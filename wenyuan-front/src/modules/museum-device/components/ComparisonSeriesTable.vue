<template>
  <NDataTable
    :columns="columns"
    :data="rows"
    :pagination="pagination"
    :scroll-x="scrollX"
    :max-height="560"
    :row-key="(row) => row.occurredAt"
    size="small"
  />
</template>

<script setup lang="ts">
import { computed, h } from 'vue';
import { NDataTable, NTooltip, type DataTableColumns } from 'naive-ui';
import { formatDateTime } from '@/utils';
import type { MuseumComparisonVo } from '../typings';
import { comparisonSeriesName, comparisonTableRows, type MuseumComparisonTableRow } from '../utils/comparison';
import { formatMetricNumber, formatMetricValue } from '../utils/metricValue';

const props = defineProps<{ data: MuseumComparisonVo }>();
const rows = computed(() => comparisonTableRows(props.data));
const pagination = { pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100] };
const scrollX = computed(() => 190 + props.data.series.length * 210);

const columns = computed<DataTableColumns<MuseumComparisonTableRow>>(() => [
  {
    title: '时间桶',
    key: 'occurredAt',
    width: 190,
    fixed: 'left',
    render: (row) => formatDateTime(row.occurredAt),
  },
  ...props.data.series.map((series) => ({
    title: () =>
      h('div', { style: 'line-height:1.35' }, [
        h('div', { style: 'font-weight:600' }, comparisonSeriesName(series)),
        h('div', { style: 'font-size:11px;color:#999' }, `${series.key}${series.unit ? ` · ${series.unit}` : ''}`),
      ]),
    key: series.seriesId,
    width: 210,
    render: (row: MuseumComparisonTableRow) => {
      const point = row.values[series.seriesId];
      if (!point) return h('span', { style: 'color:#aaa' }, '--');
      const value = formatMetricValue({ numericValue: point.value, unit: series.unit });
      return h(
        NTooltip,
        { trigger: 'hover' },
        {
          trigger: () => h('span', { style: 'font-weight:600' }, value),
          default: () =>
            `样本 ${point.count} 个；最小 ${formatMetricNumber(point.minValue)}；最大 ${formatMetricNumber(point.maxValue)}`,
        },
      );
    },
  })),
]);
</script>
