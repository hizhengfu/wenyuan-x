<template>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="incidentApi.page"
    :params="query"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="drawerRef?.create()"
    @row-view="view"
    ><template #queryForm
      ><NFormItem label="事故案卷"
        ><NInput v-model:value="query.keyword" clearable /></NFormItem></template></HPageDataTable
  ><IncidentDrawer ref="drawerRef" @saved="tableRef?.handleRefresh?.()" />
</template>
<script setup lang="ts">
import { h, reactive, ref } from 'vue';
import { NButton } from 'naive-ui';
import { incidentApi } from '../api/incident';
import IncidentDrawer from '../components/IncidentDrawer.vue';
const tableRef = ref<any>(),
  drawerRef = ref<any>(),
  query = reactive({ keyword: '' });
const columns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 200, title: '事故案卷', key: 'title', columnType: 'title', minWidth: 200 },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '当前案卷',
    key: 'closed',
    width: 170,
    render: (r: any) => (r.closed ? '本版已共同签署归档' : '本版办理中'),
  },
  { title: '登记时间', key: 'createTime', columnType: 'dateTime', width: 190 },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 160,
    render: (r: any) => h(NButton, { text: true, type: 'primary', onClick: () => view(r) }, () => '报告与实际处置'),
  },
];
function view(row: any) {
  drawerRef.value?.open(row.id);
}
</script>
