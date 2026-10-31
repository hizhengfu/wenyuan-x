<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="日记日期核对"
    ><NSpace vertical :size="16"
      ><NDatePicker v-model:value="range" type="daterange" /><NButton :loading="busy" @click="load"
        >核对各库每日记录</NButton
      ><NCard v-for="item in rows" :key="item.warehouseId" :title="item.warehouseName" size="small"
        ><NAlert v-if="!item.staffingVerified" type="warning">实际库管任职尚待核实。</NAlert
        ><NText>{{
          item.missingDates?.length
            ? `未见结构化日记：${item.missingDates.map((d: any) => formatLocalDateValue(d)).join('、')}`
            : '所选日期均已记录日记'
        }}</NText></NCard
      ></NSpace
    ></HPageForm
  >
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { formatLocalDateValue } from '@/utils';
import { warehouseLogApi } from '../api/warehouseLog';
const show = ref(false),
  busy = ref(false),
  rows = ref<any[]>([]),
  range = ref<[number, number] | null>(null);
function open() {
  show.value = true;
  const today = new Date();
  range.value = [
    new Date(today.getFullYear(), today.getMonth(), 1).getTime(),
    new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime(),
  ];
  load();
}
async function load() {
  if (!range.value) return;
  busy.value = true;
  try {
    rows.value = ((await warehouseLogApi.gaps(range.value[0], range.value[1])) as any).data || [];
  } finally {
    busy.value = false;
  }
}
defineExpose({ open });
</script>
