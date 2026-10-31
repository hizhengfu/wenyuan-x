<template>
  <div class="stat-breakdown" role="img" :aria-label="description">
    <VChart v-if="chartRows.length" :option="option" autoresize class="breakdown-chart" aria-hidden="true" />
    <n-empty v-else size="small" description="暂无分布数据" class="breakdown-empty" />
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useThemeVars } from 'naive-ui';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
use([CanvasRenderer, BarChart, GridComponent, TooltipComponent]);
const props = defineProps<{ rows: Record<string, any>[] }>();
const theme = useThemeVars();
// 图表展示数量最多的八项，完整分类和明细仍由旁边的表格展示。
const chartRows = computed(() =>
  props.rows
    .filter((row) => Number(row.count) > 0)
    .slice()
    .sort((a, b) => Number(b.count) - Number(a.count))
    .slice(0, 8),
);
const description = computed(() =>
  chartRows.value.length ? chartRows.value.map((row) => `${row.label}：${row.count}`).join('；') : '暂无分布数据',
);
const option = computed(() => ({
  animation: false,
  textStyle: { color: theme.value.textColor2, fontFamily: theme.value.fontFamily, fontSize: 11 },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    backgroundColor: theme.value.cardColor,
    borderColor: theme.value.borderColor,
    textStyle: { color: theme.value.textColor1 },
  },
  grid: { left: 0, right: 24, top: 12, bottom: 0, containLabel: true },
  xAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: theme.value.textColor3 },
    splitLine: { lineStyle: { color: theme.value.borderColor, type: 'dashed' } },
  },
  yAxis: {
    type: 'category',
    inverse: true,
    data: chartRows.value.map((row) => row.label),
    axisTick: { show: false },
    axisLine: { show: false },
    axisLabel: { width: 90, overflow: 'truncate', color: theme.value.textColor2 },
  },
  series: [
    {
      type: 'bar',
      data: chartRows.value.map((row) => Number(row.count)),
      barMaxWidth: 16,
      showBackground: true,
      backgroundStyle: { color: theme.value.actionColor, borderRadius: 4 },
      itemStyle: { color: theme.value.primaryColor, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: theme.value.textColor2, fontSize: 11 },
    },
  ],
}));
</script>
<style scoped>
.stat-breakdown {
  min-width: 0;
}
.breakdown-chart {
  height: 240px;
}
.breakdown-empty {
  height: 240px;
  display: flex;
  justify-content: center;
}
</style>
