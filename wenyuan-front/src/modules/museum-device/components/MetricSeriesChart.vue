<template>
  <div ref="containerRef" class="metric-series-chart" :style="{ height }" />
</template>

<script setup lang="ts">
import { nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';
import type { MuseumMetricSeriesVo } from '../typings';
import { formatMetricValue } from '../utils/metricValue';

const props = withDefaults(
  defineProps<{
    data: MuseumMetricSeriesVo;
    height?: string;
    color?: string;
  }>(),
  {
    height: '360px',
    color: '#2080f0',
  },
);
const emit = defineEmits<{
  pointClick: [value: { occurredAt: number; value: number; seriesName: string }];
}>();

const containerRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

async function render() {
  await nextTick();
  if (!containerRef.value) return;
  if (!chart) {
    chart = echarts.init(containerRef.value);
    chart.on('click', (params) => {
      const point = Array.isArray(params.value) ? params.value : null;
      if (!point || point.length < 2) return;
      emit('pointClick', {
        occurredAt: Number(point[0]),
        value: Number(point[1]),
        seriesName: String(params.seriesName || ''),
      });
    });
  }
  const statistics = props.data.mode === 'STATISTICS';
  const unit = props.data.unit || '';
  const palette = [props.color, '#18a058', '#f0a020', '#8a5cf5', '#d03050'];
  const fieldRows = props.data.fields?.length
    ? props.data.fields
    : [{ fieldCode: 'VALUE', fieldName: props.data.name || props.data.key, points: props.data.points || [] }];
  const series: echarts.SeriesOption[] = fieldRows.map((field, index) => {
    const points = (field.points || [])
      .filter((point) => Number.isFinite(Number(point.value)) && Number.isFinite(point.occurredAt))
      .sort((a, b) => a.occurredAt - b.occurredAt);
    const color = palette[index % palette.length];
    return {
      name: field.fieldName || field.fieldCode,
      type: 'line',
      smooth: true,
      showSymbol: points.length < 80,
      symbolSize: 5,
      data: points.map((point) => [point.occurredAt, point.value]),
      itemStyle: { color },
      lineStyle: { color, width: 2 },
      areaStyle: fieldRows.length === 1 ? { color, opacity: 0.08 } : undefined,
    };
  });
  const scalarPoints = (props.data.points || [])
    .filter((point) => Number.isFinite(Number(point.value)) && Number.isFinite(point.occurredAt))
    .sort((a, b) => a.occurredAt - b.occurredAt);
  if (statistics && fieldRows.length === 1) {
    const statisticsPoints = (fieldRows[0].points || scalarPoints)
      .filter((point) => Number.isFinite(Number(point.value)) && Number.isFinite(point.occurredAt))
      .sort((a, b) => a.occurredAt - b.occurredAt);
    series.push(
      {
        name: '最小值',
        type: 'line',
        showSymbol: false,
        data: statisticsPoints.map((point) => [point.occurredAt, point.minValue]),
        lineStyle: { color: '#18a058', width: 1, type: 'dashed' },
        itemStyle: { color: '#18a058' },
      },
      {
        name: '最大值',
        type: 'line',
        showSymbol: false,
        data: statisticsPoints.map((point) => [point.occurredAt, point.maxValue]),
        lineStyle: { color: '#d03050', width: 1, type: 'dashed' },
        itemStyle: { color: '#d03050' },
      },
    );
  }
  chart.setOption(
    {
      animationDuration: 300,
      color: palette,
      legend: { show: fieldRows.length > 1 || statistics, top: 0 },
      tooltip: {
        trigger: 'axis',
        confine: true,
        axisPointer: { type: 'cross' },
        valueFormatter: (value: unknown) => formatMetricValue({ numericValue: Number(value), unit }),
      },
      grid: { left: 52, right: 26, top: fieldRows.length > 1 || statistics ? 42 : 24, bottom: 64, containLabel: true },
      xAxis: { type: 'time', boundaryGap: false, axisPointer: { snap: true }, axisLabel: { hideOverlap: true } },
      yAxis: { type: 'value', name: unit, scale: true },
      dataZoom: [
        { type: 'inside', filterMode: 'none' },
        { type: 'slider', filterMode: 'none', height: 18, bottom: 10 },
      ],
      series,
    },
    { notMerge: true },
  );
}

function observeResize() {
  if (!containerRef.value || resizeObserver) return;
  resizeObserver = new ResizeObserver(() => chart?.resize());
  resizeObserver.observe(containerRef.value);
}

function disposeChart() {
  resizeObserver?.disconnect();
  resizeObserver = null;
  chart?.dispose();
  chart = null;
}

watch(() => [props.data, props.color], render, { deep: true });

onMounted(() => {
  render();
  observeResize();
});
onActivated(() => {
  render();
  observeResize();
});
onDeactivated(disposeChart);
onBeforeUnmount(disposeChart);
</script>

<style scoped>
.metric-series-chart {
  width: 100%;
  min-height: 280px;
}
</style>
