<template>
  <div ref="containerRef" class="pv-location-trend-chart" :style="{ height: chartHeight }" />
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';
import type { EnvironmentCardVo, HistoryDataVo } from '../typings';
import { hasLocationAssociation } from '../utils/environmentSample';

const props = defineProps<{
  data: HistoryDataVo[];
  metrics: EnvironmentCardVo[];
}>();

interface SensorGroup {
  sensorId: string;
  sensorCode?: string;
  sensorName: string;
  sensorUnit?: string;
  sensorSort: number;
  metrics: EnvironmentCardVo[];
  rows: HistoryDataVo[];
}

interface ChartPanel {
  title: string;
  groups: SensorGroup[];
  left: string | number;
  right?: string | number;
  width?: string | number;
  top: number;
  height: number;
}

interface SeriesSource {
  key: string;
  pointId?: string;
  deviceId?: string;
}

const containerRef = ref<HTMLDivElement | null>(null);
const chartHeight = ref('420px');
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

const palette = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#06b6d4', '#ec4899'];

function isTemperature(group: SensorGroup): boolean {
  return group.sensorCode?.toUpperCase() === 'TEMP' || group.sensorName.includes('温度');
}

function isHumidity(group: SensorGroup): boolean {
  return group.sensorCode?.toUpperCase() === 'HUMIDITY' || group.sensorName.includes('湿度');
}

function buildGroups(): SensorGroup[] {
  const map = new Map<string, SensorGroup>();
  props.metrics.forEach((metric) => {
    const sensorId = metric.sensorId;
    if (!sensorId) return;
    const current = map.get(sensorId);
    if (current) {
      current.metrics.push(metric);
      return;
    }
    map.set(sensorId, {
      sensorId,
      sensorCode: metric.sensorCode,
      sensorName: metric.sensorName || '未知指标',
      sensorUnit: metric.sensorUnit,
      sensorSort: metric.sensorSort ?? Number.MAX_SAFE_INTEGER,
      metrics: [metric],
      rows: [],
    });
  });
  props.data.forEach((row) => {
    if (!row.sensorId) return;
    let group = map.get(row.sensorId);
    if (!group) {
      group = {
        sensorId: row.sensorId,
        sensorName: row.sensorName || '未知指标',
        sensorUnit: row.sensorUnit,
        sensorSort: Number.MAX_SAFE_INTEGER,
        metrics: [],
        rows: [],
      };
      map.set(row.sensorId, group);
    }
    group.rows.push(row);
  });
  return [...map.values()].sort(
    (a, b) => a.sensorSort - b.sensorSort || a.sensorName.localeCompare(b.sensorName, 'zh-CN'),
  );
}

function buildPanels(groups: SensorGroup[]): ChartPanel[] {
  const panels: ChartPanel[] = [];
  groups.forEach((group, index) => {
    panels.push({
      title: isTemperature(group) ? '温度趋势' : isHumidity(group) ? '相对湿度趋势' : group.sensorName,
      groups: [group],
      left: 64,
      right: 64,
      top: 78 + index * 258,
      height: 180,
    });
  });

  if (!panels.length) {
    panels.push({ title: '监测趋势', groups: [], left: 64, right: 32, top: 70, height: 220 });
  }
  return panels;
}

function seriesSourceKey(pointId?: string, deviceId?: string): string {
  return `${pointId || 'NO_POINT'}::${deviceId || 'NO_DEVICE'}`;
}

function metricFor(group: SensorGroup, source: SeriesSource): EnvironmentCardVo | undefined {
  if (!source.pointId) return undefined;
  return (
    group.metrics.find((metric) => metric.sensorPointId === source.pointId && metric.deviceId === source.deviceId) ||
    group.metrics[0]
  );
}

function safeMarkLine(metric: EnvironmentCardVo | undefined, name: string) {
  if (!metric || metric.thresholdConflict || (metric.safeMinValue == null && metric.safeMaxValue == null)) {
    return undefined;
  }
  const data = [
    metric.safeMinValue == null ? null : { name: `${name}当前标准参考下限`, yAxis: metric.safeMinValue },
    metric.safeMaxValue == null ? null : { name: `${name}当前标准参考上限`, yAxis: metric.safeMaxValue },
  ].filter(Boolean);
  return {
    silent: true,
    symbol: ['none', 'none'],
    lineStyle: { color: '#2080f0', type: 'dashed', width: 1 },
    label: {
      position: 'middle',
      color: '#2080f0',
      backgroundColor: 'rgba(255, 255, 255, 0.92)',
      borderRadius: 3,
      padding: [2, 5],
      formatter: '{b}: {c}',
    },
    data,
  };
}

function safeMarkArea(metric: EnvironmentCardVo | undefined) {
  if (metric?.thresholdConflict || metric?.safeMinValue == null || metric.safeMaxValue == null) return undefined;
  return {
    silent: true,
    itemStyle: { color: 'rgba(32, 128, 240, 0.05)' },
    data: [[{ name: '当前标准参考区间', yAxis: metric.safeMinValue }, { yAxis: metric.safeMaxValue }]],
  };
}

async function render() {
  const groups = buildGroups();
  const multipleDevices =
    new Set(
      [...props.metrics.map((metric) => metric.deviceId), ...props.data.map((row) => row.deviceId)].filter(Boolean),
    ).size > 1;
  const panels = buildPanels(groups);
  const lastPanel = panels.reduce((last, panel) => Math.max(last, panel.top + panel.height), 0);
  chartHeight.value = `${Math.max(360, lastPanel + 76)}px`;
  await nextTick();
  if (!containerRef.value) return;
  if (!chart) chart = echarts.init(containerRef.value);

  const grids: any[] = [];
  const titles: any[] = [];
  const legends: any[] = [];
  const xAxes: any[] = [];
  const yAxes: any[] = [];
  const series: any[] = [];

  panels.forEach((panel, panelIndex) => {
    grids.push({
      left: panel.left,
      right: panel.right,
      width: panel.width,
      top: panel.top,
      height: panel.height,
      containLabel: true,
    });
    titles.push({
      text: panel.title,
      left: panel.left,
      top: panel.top - 56,
      textStyle: { fontSize: 14, fontWeight: 600 },
    });
    xAxes.push({
      type: 'time',
      gridIndex: panelIndex,
      boundaryGap: false,
      axisPointer: { show: true, snap: true, label: { show: true } },
      axisLabel: { hideOverlap: true },
    });

    const firstGroup = panel.groups[0];
    yAxes.push({
      type: 'value',
      gridIndex: panelIndex,
      name: firstGroup?.sensorUnit || '',
      position: 'left',
      scale: true,
    });

    const panelSeriesNames: string[] = [];
    panel.groups.forEach((group, groupIndex) => {
      const sourceMap = new Map<string, SeriesSource>();
      if (!group.rows.length) {
        group.metrics.forEach((metric) => {
          const key = seriesSourceKey(metric.sensorPointId, metric.deviceId || undefined);
          sourceMap.set(key, { key, pointId: metric.sensorPointId, deviceId: metric.deviceId || undefined });
        });
      }
      group.rows.forEach((row) => {
        const key = seriesSourceKey(row.sensorPointId, row.deviceId);
        sourceMap.set(key, { key, pointId: row.sensorPointId, deviceId: row.deviceId });
      });
      const sources = sourceMap.size ? [...sourceMap.values()] : [{ key: 'NO_SOURCE' }];
      sources.forEach((source, sourceIndex) => {
        const metric = metricFor(group, source);
        const sampleRow = group.rows.find(
          (row) => row.sensorPointId === source.pointId && row.deviceId === source.deviceId,
        );
        const pointName = metric?.sensorPointName || sampleRow?.sensorPointName;
        const deviceName = metric?.deviceName || sampleRow?.deviceName || source.deviceId;
        const sourceName = [deviceName, pointName && pointName !== deviceName ? pointName : null]
          .filter(Boolean)
          .join(' · ');
        const showSource = sources.length > 1 || multipleDevices;
        const baseName = showSource && sourceName ? `${group.sensorName} · ${sourceName}` : group.sensorName;
        const name =
          sampleRow && !hasLocationAssociation(sampleRow.sampleLocationBasis)
            ? `${baseName} · 历史位置待核实`
            : baseName;
        const lineRows = group.rows
          .filter((row) => row.sensorPointId === source.pointId && row.deviceId === source.deviceId)
          .filter(
            (row) =>
              row.dateTime &&
              row.dataValue != null &&
              Number.isFinite(new Date(row.dateTime).getTime()) &&
              Number.isFinite(Number(row.dataValue)),
          )
          .sort((a, b) => new Date(a.dateTime!).getTime() - new Date(b.dateTime!).getTime());
        const yAxisIndex = yAxes.length - 1;
        const color = isTemperature(group)
          ? ['#ef4444', '#f97316', '#fb7185', '#dc2626', '#f59e0b'][sourceIndex % 5]
          : isHumidity(group)
            ? ['#2563eb', '#06b6d4', '#60a5fa', '#1d4ed8', '#0891b2'][sourceIndex % 5]
            : palette[(panelIndex + groupIndex + sourceIndex) % palette.length];
        panelSeriesNames.push(name);
        series.push({
          name,
          type: 'line',
          xAxisIndex: panelIndex,
          yAxisIndex,
          data: lineRows.map((row) => [new Date(row.dateTime!).getTime(), Number(row.dataValue)]),
          smooth: true,
          showSymbol: false,
          connectNulls: false,
          itemStyle: { color },
          lineStyle: { color, width: 2 },
          areaStyle: panel.groups.length === 1 && sources.length === 1 ? { color, opacity: 0.08 } : undefined,
          markLine: safeMarkLine(metric, showSource && sourceName ? sourceName : group.sensorName),
          markArea: sources.length === 1 ? safeMarkArea(metric) : undefined,
          tooltip: {
            valueFormatter: (value: unknown) => `${value ?? '--'}${group.sensorUnit ? ` ${group.sensorUnit}` : ''}`,
          },
        });
      });
    });
    if (panelSeriesNames.length > 1) {
      legends.push({
        data: panelSeriesNames,
        type: 'scroll',
        left: panel.left,
        right: panel.right,
        width: panel.width,
        top: panel.top - 32,
      });
    }
  });

  chart.setOption(
    {
      animationDuration: 300,
      title: titles,
      legend: legends,
      tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, confine: true },
      axisPointer: {
        link: [{ xAxisIndex: xAxes.map((_, index) => index) }],
        label: { backgroundColor: '#5b6472' },
      },
      toolbox: { right: 16, top: 8, feature: { dataZoom: {}, restore: {}, saveAsImage: {} } },
      grid: grids,
      xAxis: xAxes,
      yAxis: yAxes,
      dataZoom: [{ type: 'inside', xAxisIndex: xAxes.map((_, index) => index), filterMode: 'none' }],
      series,
    },
    { notMerge: true },
  );
  chart.resize();
}

function resize() {
  chart?.resize();
}

watch(() => [props.data, props.metrics], render, { deep: true });
onMounted(() => {
  render();
  if (containerRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => chart?.resize());
    resizeObserver.observe(containerRef.value);
  }
  window.addEventListener('resize', resize);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  resizeObserver?.disconnect();
  resizeObserver = null;
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
.pv-location-trend-chart {
  width: 100%;
  min-height: 360px;
}
</style>
