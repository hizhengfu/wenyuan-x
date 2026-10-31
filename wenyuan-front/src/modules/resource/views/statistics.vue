<template>
  <ResourceWorkbench @refresh="loadAll">
    <dl class="resource-stat-strip">
      <div v-for="item in overviewCards" :key="item.label">
        <dt>{{ item.label }}</dt>
        <dd>{{ item.value }}</dd>
      </div>
    </dl>
    <div class="resource-chart-grid">
      <n-card title="资源类型" class="resource-chart"
        ><HEChart :option="typePieOption" :loading="typeLoading"
      /></n-card>
      <n-card title="资源分类" class="resource-chart"
        ><HEChart :option="categoryBarOption" :loading="categoryLoading"
      /></n-card>
      <n-card title="资源入库" class="resource-chart"
        ><template #header-extra
          ><n-radio-group v-model:value="trendDays" size="small" @update:value="loadTrend"
            ><n-radio-button :value="7">7天</n-radio-button><n-radio-button :value="30">30天</n-radio-button
            ><n-radio-button :value="90">90天</n-radio-button></n-radio-group
          ></template
        ><HEChart :option="trendOption" :loading="trendLoading"
      /></n-card>
      <n-card title="资源访问" class="resource-chart"
        ><template #header-extra
          ><n-radio-group v-model:value="usageDays" size="small" @update:value="loadUsageTrend"
            ><n-radio-button :value="7">7天</n-radio-button><n-radio-button :value="30">30天</n-radio-button
            ><n-radio-button :value="90">90天</n-radio-button></n-radio-group
          ></template
        ><HEChart :option="usageTrendOption" :loading="usageLoading"
      /></n-card>
    </div>
  </ResourceWorkbench>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue';
import { useThemeVars } from 'naive-ui';
const theme = useThemeVars();
import ResourceWorkbench from '../components/ResourceWorkbench.vue';
import HEChart from '@/components/data/chart/EChart.vue';
import { overview, byType, byCategory, trend, usageTrend, formatSize } from '../api/stat';
import { RESOURCE_TYPE_OPTIONS } from '../api/resource';

// ==== 概览 ====
const overviewData = ref<Record<string, any>>({});
const overviewCards = computed(() => [
  { label: '已发布', value: overviewData.value.publishedCount ?? '—', color: '#65918a' },
  {
    label: '草稿 / 归档',
    value: `${overviewData.value.draftCount ?? '—'} / ${overviewData.value.archivedCount ?? '—'}`,
    color: '#f0a020',
  },
  {
    label: '存储占用',
    value: formatSize(Number(overviewData.value.totalSize ?? '—')),
    color: '#9a6cf0',
  },
  {
    label: '浏览 / 下载',
    value: `${overviewData.value.totalViewCount ?? '—'} / ${overviewData.value.totalDownloadCount ?? '—'}`,
    color: '#d03050',
  },
]);

// ==== 类型分布 ====
const typeLoading = ref(false);
const typeData = ref<any[]>([]);
const typePieOption = computed(() => ({
  color: [theme.value.primaryColor, '#9682b5', '#65918a', '#a6b9b3', '#bfae94', '#c4c6cc'],
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'horizontal', bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['45%', '70%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: typeData.value.map((d: any) => ({
        name: RESOURCE_TYPE_OPTIONS.find((o) => o.value === d.type)?.label || d.type,
        value: Number(d.count ?? 0),
      })),
    },
  ],
}));

// ==== 分类分布 ====
const categoryLoading = ref(false);
const categoryData = ref<any[]>([]);
const categoryBarOption = computed(() => {
  const top10 = categoryData.value.slice(0, 10);
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 24, top: 20, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: top10.map((d: any) => d.categoryName || '未分类').reverse(),
      axisTick: { alignWithLabel: true },
    },
    series: [
      {
        type: 'bar',
        barWidth: 16,
        data: top10.map((d: any) => Number(d.count ?? 0)).reverse(),
        itemStyle: { color: theme.value.primaryColor, borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', formatter: '{c}' },
      },
    ],
  };
});

// ==== 趋势图 ====
const trendLoading = ref(false);
const trendDays = ref(30);
const trendData = ref<any[]>([]);
const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trendData.value.map((d: any) => d.date),
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: trendData.value.map((d: any) => Number(d.count ?? 0)),
      itemStyle: { color: theme.value.primaryColor },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(124,77,255,0.12)' },
            { offset: 1, color: 'rgba(124,77,255,0)' },
          ],
        },
      },
    },
  ],
}));

const usageLoading = ref(false);
const usageDays = ref(30);
const usageData = ref<any[]>([]);
const usageTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: usageData.value.map((d: any) => d.date),
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: usageData.value.map((d: any) => Number(d.count ?? 0)),
      itemStyle: { color: '#65918a' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(101,145,138,0.12)' },
            { offset: 1, color: 'rgba(101,145,138,0)' },
          ],
        },
      },
    },
  ],
}));

async function loadOverview() {
  try {
    const resp: any = await overview();
    overviewData.value = resp.data || {};
  } catch {
    overviewData.value = {};
  }
}

async function loadByType() {
  typeLoading.value = true;
  try {
    const resp: any = await byType();
    typeData.value = resp.data || [];
  } finally {
    typeLoading.value = false;
  }
}

async function loadByCategory() {
  categoryLoading.value = true;
  try {
    const resp: any = await byCategory();
    categoryData.value = resp.data || [];
  } finally {
    categoryLoading.value = false;
  }
}

async function loadTrend() {
  trendLoading.value = true;
  try {
    const resp: any = await trend(trendDays.value);
    trendData.value = resp.data || [];
  } finally {
    trendLoading.value = false;
  }
}

async function loadUsageTrend() {
  usageLoading.value = true;
  try {
    const resp: any = await usageTrend(usageDays.value);
    usageData.value = resp.data || [];
  } finally {
    usageLoading.value = false;
  }
}

function loadAll() {
  loadOverview();
  loadByType();
  loadByCategory();
  loadTrend();
  loadUsageTrend();
}

onMounted(loadAll);
</script>
