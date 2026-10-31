<template>
  <div ref="containerRef" class="pv-trend-chart" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';

const props = defineProps<{
  data: { dateTime?: number; dataValue?: number }[];
  unit?: string;
  color?: string;
  height?: string;
  safeMin?: number | null;
  safeMax?: number | null;
}>();

const containerRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

function render() {
  if (!containerRef.value) return;
  if (!chart) chart = echarts.init(containerRef.value);
  const source = (props.data ?? [])
    .filter((d) => d.dateTime && d.dataValue != null && Number.isFinite(d.dataValue))
    .map((d) => ({ dateTime: d.dateTime, dataValue: d.dataValue }))
    .sort((a, b) => new Date(a.dateTime!).getTime() - new Date(b.dateTime!).getTime());
  const safeBoundaries = [
    props.safeMin == null ? null : { name: '标准下限', yAxis: props.safeMin },
    props.safeMax == null ? null : { name: '标准上限', yAxis: props.safeMax },
  ].filter(Boolean);
  chart.setOption({
    animationDuration: 300,
    dataset: { dimensions: ['dateTime', 'dataValue'], source },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      valueFormatter: (value: unknown) => `${value ?? '--'}${props.unit ? ` ${props.unit}` : ''}`,
    },
    grid: { left: 56, right: 24, top: 28, bottom: 62, containLabel: true },
    xAxis: { type: 'time', boundaryGap: false, axisPointer: { snap: true } },
    yAxis: { type: 'value', name: props.unit || '', scale: true },
    dataZoom: [
      { type: 'inside', filterMode: 'none' },
      { type: 'slider', filterMode: 'none', height: 18, bottom: 10 },
    ],
    series: [{
      name: '监测值',
      type: 'line',
      smooth: true,
      showSymbol: false,
      encode: { x: 'dateTime', y: 'dataValue' },
      itemStyle: { color: props.color || '#3b82f6' },
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.1 },
      markArea: props.safeMin != null && props.safeMax != null ? {
        silent: true,
        itemStyle: { color: 'rgba(24, 160, 88, 0.08)' },
        data: [[{ name: '标准区间', yAxis: props.safeMin }, { yAxis: props.safeMax }]],
      } : undefined,
      markLine: safeBoundaries.length ? {
        silent: true,
        symbol: ['none', 'none'],
        lineStyle: { color: '#18a058', type: 'dashed', width: 1 },
        label: {
          position: 'middle',
          color: '#18a058',
          backgroundColor: 'rgba(255, 255, 255, 0.9)',
          borderRadius: 3,
          padding: [2, 5],
          formatter: '{b}: {c}',
        },
        data: safeBoundaries,
      } : undefined,
    }],
  }, { notMerge: true });
}

function resize() { chart?.resize(); }

watch(
  () => [props.data, props.unit, props.color, props.safeMin, props.safeMax],
  render,
  { deep: true },
);

onMounted(() => {
  render();
  window.addEventListener('resize', resize);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
.pv-trend-chart {
  width: 100%;
  height: v-bind('props.height || "260px"');
}
</style>
