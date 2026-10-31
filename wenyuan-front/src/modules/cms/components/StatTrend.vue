<template>
  <div class="stat-trend" role="img" :aria-label="description">
    <VChart v-if="rows.length" :option="option" autoresize class="trend-chart" aria-hidden="true" />
    <n-empty v-else size="small" description="所选时间内暂无记录" class="trend-empty" />
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useThemeVars } from 'naive-ui';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent]);
const props = withDefaults(defineProps<{ rows: Record<string, any>[]; metrics?: { key: string; label: string }[] }>(), {
  metrics: () => [
    { key: 'pv', label: 'PV' },
    { key: 'uv', label: 'UV' },
  ],
});
const theme = useThemeVars();
const description = computed(() =>
  props.rows.length
    ? `${props.metrics.map((metric) => metric.label).join('、')}趋势，${props.rows[0].day} 至 ${props.rows[props.rows.length - 1].day}`
    : '所选时间内暂无记录',
);
const option = computed(() => ({
  animation: false,
  color: [
    theme.value.primaryColor,
    theme.value.infoColor,
    theme.value.successColor,
    theme.value.warningColor,
    theme.value.errorColor,
  ],
  textStyle: { color: theme.value.textColor2, fontFamily: theme.value.fontFamily, fontSize: 11 },
  tooltip: {
    trigger: 'axis',
    backgroundColor: theme.value.cardColor,
    borderColor: theme.value.borderColor,
    textStyle: { color: theme.value.textColor1 },
  },
  legend: {
    type: 'scroll',
    left: 0,
    top: 0,
    icon: 'circle',
    itemWidth: 7,
    itemHeight: 7,
    textStyle: { color: theme.value.textColor2, fontSize: 11 },
    data: props.metrics.map((metric) => metric.label),
  },
  grid: { left: 4, right: 12, top: 40, bottom: 4, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: props.rows.map((row) => row.day),
    axisLine: { lineStyle: { color: theme.value.borderColor } },
    axisTick: { show: false },
    axisLabel: { color: theme.value.textColor3, formatter: (value: string) => value.slice(5) },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: theme.value.textColor3 },
    splitLine: { lineStyle: { color: theme.value.borderColor, type: 'dashed' } },
  },
  // 采集前的日期保持缺口，不能在图表中被补成零访问。
  series: props.metrics.map((metric) => ({
    name: metric.label,
    type: 'line',
    connectNulls: false,
    showSymbol: props.rows.length <= 14,
    symbolSize: 5,
    lineStyle: { width: 2 },
    areaStyle: { opacity: 0.05 },
    data: props.rows.map((row) => (row.available === false ? null : row[metric.key])),
  })),
}));
</script>
<style scoped>
.stat-trend {
  min-width: 0;
}
.trend-chart {
  height: 240px;
}
.trend-empty {
  height: 160px;
  display: flex;
  justify-content: center;
}
@media (max-width: 700px) {
  .trend-chart {
    height: 220px;
  }
  .trend-empty {
    height: 160px;
  }
}
</style>
