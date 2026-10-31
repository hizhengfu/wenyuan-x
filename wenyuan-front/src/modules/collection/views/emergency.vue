<template>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="emergencyApi.page"
    :params="query"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    row-key-field="rowKey"
    @row-view="view"
    ><template #queryForm
      ><NFormItem label="收藏单位"><NInput v-model:value="query.keyword" clearable /></NFormItem
      ><NFormItem label="演练状态"
        ><NSelect
          v-model:value="query.status"
          :options="statusOptions"
          clearable
          style="min-width: 230px" /></NFormItem></template
  ></HPageDataTable>
  <CollectionEmergencyDrawer ref="drawerRef" @saved="tableRef?.handleRefresh?.()" />
</template>
<script setup lang="ts">
import { h, reactive, ref } from 'vue';
import { NButton } from 'naive-ui';
import { emergencyApi } from '../api/emergency';
import { emergencyScenarios, emergencyStatuses } from '../utils/emergencyFields';
import CollectionEmergencyDrawer from '../components/CollectionEmergencyDrawer.vue';
const tableRef = ref<any>(),
  drawerRef = ref<any>(),
  query = reactive({ keyword: '', status: null as string | null });
const statusOptions = Object.entries(emergencyStatuses).map(([value, label]) => ({ value, label }));
const columns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '收藏单位', key: 'name', columnType: 'title', minWidth: 180 },
  { ellipsis: { tooltip: true }, resizable: true, title: '风险情景', key: 'scenario', width: 120, render: (r: any) => emergencyScenarios[r.scenario] || r.scenario },
  { ellipsis: { tooltip: true }, resizable: true, width: 250,
    title: '预案及演练状态',
    key: 'status',
    minWidth: 250,
    render: (r: any) => emergencyStatuses[r.status] || r.status,
  },
  { title: '下次演练期限', key: 'nextDueAt', columnType: 'dateTime', width: 190 },
  { title: '本馆待评估', key: 'pendingEvaluationCount', width: 110 },
  { title: '本馆待整改', key: 'pendingIssueCount', width: 110 },
  { title: '待交接或承接', key: 'pendingDutyCount', width: 130 },
  { title: '关联事故办理中', key: 'openIncidentCount', width: 130 },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 150,
    render: (r: any) => h(NButton, { text: true, type: 'primary', onClick: () => view(r) }, () => '预案演练与整改'),
  },
];
function view(row: any) {
  drawerRef.value?.open(row.orgId);
}
</script>
