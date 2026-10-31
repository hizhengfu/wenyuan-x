<template>
  <NEmpty v-if="!items?.length" description="无关联藏品" size="small" />
  <NDataTable v-else :columns="columns" :data="items" size="small" :bordered="false" :max-height="240" />
</template>

<script setup lang="ts">
import { h } from 'vue';
import { NButton, NDataTable, NEmpty, type DataTableColumns } from 'naive-ui';

const props = defineProps<{ items?: any[] }>();

const columns: DataTableColumns<any> = [
  { title: '总登记号', key: 'totalRegNo', width: 140, ellipsis: { tooltip: true }, resizable: true },
  { title: '名称', key: 'name', minWidth: 160, resizable: true, width: 180, ellipsis: { tooltip: true } },
  { title: '级别', key: 'level', width: 120, ellipsis: { tooltip: true }, resizable: true },
  { title: '质地', key: 'texture', width: 120, ellipsis: { tooltip: true }, resizable: true },
  { title: '状态', key: 'lifeStatus', width: 130, ellipsis: { tooltip: true }, resizable: true },
  {
    title: '操作', key: 'action', width: 90, fixed: 'right',
    render: (row: any) => h(NButton, {
      size: 'tiny', quaternary: true, type: 'primary',
      onClick: () => window.open(`/#/collection/search?id=${row.id}`),
    }, () => '查看'),
  },
];
</script>
