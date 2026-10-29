<template>
  <div ref="containerRef" class="comparison-series-chart" :style="{ height: chartHeight }" />
</template>

<script setup lang="ts">
import { nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';
import { formatDateTime } from '@/utils';
import type { MuseumComparisonBucketValueVo, MuseumComparisonSeriesVo, MuseumComparisonVo } from '../typings';
import { comparisonSeriesName, comparisonUnitGroups } from '../utils/comparison';
import { formatMetricNumber, formatMetricValue } from '../utils/metricValue';

const props = defineProps<{ data: MuseumComparisonVo }>();
const containerRef = ref<HTMLDivElement | null>(null);
const chartHeight = ref('420px');
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

const palette = ['#2080f0', '#18a058', '#d03050', '#f0a020', '#8b5cf6', '#06b6d4', '#ec4899', '#64748b'];

interface ChartPanel {
  title?: string;
  units: ReturnType<typeof comparisonUnitGroups>;
}

function panels(): ChartPanel[] {
  const groups = comparisonUnitGroups(props.data.series || []);
  if (groups.length <= 2) return [{ units: groups }];
  return groups.map((group) => ({ title: group.unit, units: [group] }));
}

function bucketValue(seriesId: string, values: MuseumComparisonBucketValueVo[]) {
  return values.find((value) => value.seriesId === seriesId);
}

async function render() {
  await nextTick();
  if (!containerRef.value) return;
  if (!chart) chart = echarts.init(containerRef.value);

  const chartPanels = panels();
  const multiPanel = chartPanels.length > 1;
  const panelHeight = 210;
  const panelGap = 92;
  const firstTop = multiPanel ? 86 : 64;
  chartHeight.value = `${Math.max(420, firstTop + chartPanels.length * (panelHeight + panelGap) + 42)}px`;
  await nextTick();

  const grids: any[] = [];
  const titles: any[] = [];
  const xAxes: any[] = [];
  const yAxes: any[] = [];
  const seriesOptions: any[] = [];
  const seriesByName = new Map<string, MuseumComparisonSeriesVo>();

  chartPanels.forEach((panel, panelIndex) => {
    const top = firstTop + panelIndex * (panelHeight + panelGap);
    grids.push({ left: 70, right: 70, top, height: panelHeight, containLabel: true });
    if (panel.title) {
      titles.push({ text: panel.title, left: 70, top: top - 38, textStyle: { fontSize: 14, fontWeight: 600 } });
    }
    xAxes.push({
      type: 'time',
      gridIndex: panelIndex,
      boundaryGap: false,
      axisPointer: { show: true, snap: true, label: { show: true } },
      axisLabel: { hideOverlap: true },
    });

    const axisByUnit = new Map<string, number>();
    panel.units.forEach((group, unitIndex) => {
      const axisIndex = yAxes.length;
      axisByUnit.set(group.unit, axisIndex);
      yAxes.push({
        type: 'value',
        gridIndex: panelIndex,
        name: group.unit === '无单位' ? '' : group.unit,
        position: unitIndex === 1 ? 'right' : 'left',
        offset: unitIndex > 1 ? (unitIndex - 1) * 54 : 0,
        scale: true,
      });
    });

    panel.units.forEach((group) => {
      group.series.forEach((item) => {
        const globalIndex = props.data.series.findIndex((candidate) => candidate.seriesId === item.seriesId);
        const name = comparisonSeriesName(item);
        const color = palette[Math.max(0, globalIndex) % palette.length];
        seriesByName.set(name, item);
        seriesOptions.push({
          name,
          type: 'line',
          xAxisIndex: panelIndex,
          yAxisIndex: axisByUnit.get(group.unit),
          smooth: true,
          showSymbol: (props.data.buckets || []).length < 80,
          symbolSize: 5,
          connectNulls: false,
          data: (props.data.buckets || []).map((bucket) => {
            const point = bucketValue(item.seriesId, bucket.values || []);
            return {
              value: [bucket.occurredAt, point?.value ?? null],
              point,
            };
          }),
          itemStyle: { color },
          lineStyle: { color, width: 2 },
        });
      });
    });
  });

  const xAxisIndices = xAxes.map((_, index) => index);
  chart.setOption(
    {
      animationDuration: 300,
      title: titles,
      legend: {
        type: 'scroll',
        top: 4,
        left: 18,
        right: 160,
        data: props.data.series.map(comparisonSeriesName),
      },
      tooltip: {
        trigger: 'axis',
        confine: true,
        axisPointer: { type: 'cross' },
        formatter: (params: any) => {
          const items = Array.isArray(params) ? params : [params];
          const valid = items.filter((item: any) => item?.data?.point);
          const occurredAt = items[0]?.value?.[0];
          const lines = [occurredAt == null ? '' : `<strong>${formatDateTime(occurredAt)}</strong>`];
          valid.forEach((item: any) => {
            const meta = seriesByName.get(item.seriesName);
            const point = item.data.point as MuseumComparisonBucketValueVo;
            lines.push(
              `${item.marker}${item.seriesName}：${formatMetricValue({ numericValue: point.value, unit: meta?.unit })}` +
                `<span style="color:#999">（${point.count} 个样本，${formatMetricNumber(point.minValue)} ~ ${formatMetricNumber(point.maxValue)}）</span>`,
            );
          });
          return lines.join('<br/>');
        },
      },
      axisPointer: { link: [{ xAxisIndex: xAxisIndices }], label: { backgroundColor: '#5b6472' } },
      toolbox: { right: 16, top: 0, feature: { dataZoom: {}, restore: {}, saveAsImage: {} } },
      grid: grids,
      xAxis: xAxes,
      yAxis: yAxes,
      dataZoom: [
        { type: 'inside', xAxisIndex: xAxisIndices, filterMode: 'none' },
        { type: 'slider', xAxisIndex: xAxisIndices, filterMode: 'none', height: 18, bottom: 8 },
      ],
      series: seriesOptions,
    },
    { notMerge: true },
  );
  chart.resize();
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

watch(() => props.data, render, { deep: true });
onMounted(() => {
  void render();
  observeResize();
});
onActivated(() => {
  void render();
  observeResize();
});
onDeactivated(disposeChart);
onBeforeUnmount(disposeChart);
</script>

<style scoped>
.comparison-series-chart {
  width: 100%;
  min-height: 360px;
}
</style>
