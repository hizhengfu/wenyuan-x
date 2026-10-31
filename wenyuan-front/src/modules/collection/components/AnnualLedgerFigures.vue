<template>
  <NSpace vertical>
    <NDescriptions bordered :column="3"
      ><NDescriptionsItem v-for="f in annualCountFields" :key="f.key" :label="f.label">{{
        data[f.key] ?? '未填写'
      }}</NDescriptionsItem></NDescriptions
    >
    <NDataTable :columns="columns" :data="data.quantityBalances || data.quantities || []" :pagination="false" :scroll-x="1030" />
    <NText v-if="data.changesDescription">增减及变更说明：{{ data.changesDescription }}</NText>
  </NSpace>
</template>
<script setup lang="ts">
import { annualCountFields, annualQuantityFields } from '../utils/annualReportFields';
defineProps<{ data: any }>();
const columns = [
  { title: '计量单位', key: 'unit', width: 110, minWidth: 80, resizable: true, ellipsis: { tooltip: true } },
  ...annualQuantityFields.map((f) => ({ title: f.label, key: f.key, width: f.key === 'adjustment' ? 200 : 120 })),
];
</script>
