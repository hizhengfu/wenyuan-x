<template>
  <BorderBox7 :color="['#3181F6']">
    <HChart :option="chartOptions" style="height: 100%; width: 100%" theme="dark"></HChart>
  </BorderBox7>
</template>
<script lang="ts" setup>
import { ref, watchEffect } from 'vue';
import { chart } from '@/platforms/asa_wms/api/analysis';
import { BorderBox7 } from '@kjgl77/datav-vue3';

const chartOptions = ref({});
const loading = ref(false);
const props = defineProps({
  title: { type: String, default: '区块名称' },
  dataType: { type: String, default: '0' },
});

function fetchChartData(dataType: string) {
  loading.value = true;
  chart({ type: dataType })
    .then((resp) => {
      if (resp && resp.data) {
        chartOptions.value = resp.data[0] || {};
      }
    })
    .finally(() => {
      loading.value = false;
    });
}

watchEffect(() => {
  fetchChartData(props.dataType);
});
</script>
