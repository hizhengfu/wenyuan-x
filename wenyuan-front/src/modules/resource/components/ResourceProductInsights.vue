<template>
  <n-space vertical :size="16"
    ><n-space
      ><n-input-number
        v-model:value="year"
        placeholder="全部年度"
        :precision="0"
        :min="1900"
        :max="9998"
        clearable
      /><n-button :loading="loading" @click="load">查询</n-button></n-space
    ><n-alert v-if="error" type="error">{{ error }}</n-alert
    ><n-alert v-else-if="data && !data.hasSalesData" type="info"
      >暂无销售数据</n-alert
    ><n-grid v-if="data" cols="1 s:3" responsive="screen" :x-gap="16" :y-gap="16"
      ><n-gi
        ><n-card><n-statistic label="产品总数" :value="data.productCount" /></n-card></n-gi
      ><n-gi
        ><n-card><n-statistic label="销售数量" :value="data.hasSalesData ? data.quantity : '暂无数据'" /></n-card></n-gi
      ><n-gi
        ><n-card
          ><n-statistic
            label="销售金额（元）"
            :value="data.hasSalesData ? (data.amountCents / 100).toFixed(2) : '暂无数据'" /></n-card></n-gi></n-grid
    ><div v-if="data?.hasSalesData" class="resource-chart-grid"><div v-for="chart in charts" :key="chart.key"
        ><n-card :title="chart.title" style="height: 360px"
          ><HEChart :option="option(chart.key)" /></n-card></div></div
  ></n-space>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import { useThemeVars } from 'naive-ui';
const theme = useThemeVars();
import HEChart from '@/components/data/chart/EChart.vue';
import { insights, PRODUCT_CATEGORIES } from '../api/business';
const props = defineProps<{ revision?: number }>();
const data = ref<any>(),
  year = ref<number | null>(null),
  loading = ref(false),
  error = ref('');
let generation = 0;
const charts = [
  { key: 'byYear', title: '年度销售' },
  { key: 'byRegion', title: '地区销售' },
  { key: 'byCategory', title: '品类分析' },
  { key: 'byProduct', title: '产品销售' },
  { key: 'byChannel', title: '销售渠道' },
];
function option(key: string) {
  const rows = data.value?.[key] || [];
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 16, right: 20, bottom: 65, top: 20, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r: any) => PRODUCT_CATEGORIES.find((v) => v.value === r.label)?.label || r.label),
      axisLabel: { rotate: 25 },
    },
    yAxis: { type: 'value', name: '元' },
    series: [{ type: 'bar', barMaxWidth: 36, data: rows.map((r: any) => r.amountCents / 100), itemStyle: { color: theme.value.primaryColor, borderRadius: [4,4,0,0] } }],
  };
}
async function load() {
  const own = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const r: any = await insights(year.value || undefined);
    if (own === generation) data.value = r.data;
  } catch {
    if (own === generation) {
      data.value = undefined;
      error.value = '销售分析读取失败';
    }
  } finally {
    if (own === generation) loading.value = false;
  }
}
watch(() => props.revision, load, { immediate: true });
</script>
