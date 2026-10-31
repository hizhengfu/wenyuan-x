<template>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="samplingApi.page"
    :params="query"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="createRef?.open({})"
    @row-view="onView"
  >
    <template #queryForm
      ><NFormItem label="检索内容"><NInput v-model:value="query.keyword" placeholder="项目或藏品编号" clearable /></NFormItem
    ></template>
    <CollectionEvidenceForm
      ref="createRef"
      title="新增有损取样申请"
      :fields="samplingFields.create"
      :save="create"
      @saved="refresh"
    />
    <SamplingDrawer ref="drawerRef" @saved="refresh" />
    <FlowFormDrawer
      ref="flowRef"
      :fetch-fn="samplingApi.flow"
      :submit-api="samplingApi.submit"
      :cancel-api="samplingApi.cancel"
      @success="refresh"
    />
  </HPageDataTable>
</template>
<script setup lang="ts">
import { h, reactive, ref } from 'vue';
import { NButton } from 'naive-ui';
import { samplingApi } from '../api/sampling';
import SamplingDrawer from '../components/SamplingDrawer.vue';
import CollectionEvidenceForm from '../components/CollectionEvidenceForm.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { samplingFields } from '../utils/samplingFields';
const tableRef = ref<any>(),
  drawerRef = ref<any>(),
  flowRef = ref<any>(),
  createRef = ref<any>(),
  query = reactive({ keyword: '' });
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowRef,
  getLabel: (r: any) => r.title,
});
const columns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '取样项目', key: 'title', minWidth: 180, columnType: 'title' },
  { ellipsis: { tooltip: true }, resizable: true, title: '藏品编号', key: 'collectionId', width: 180 },
  flowStatusColumn,
  { title: '创建时间', key: 'createTime', columnType: 'dateTime', width: 190 },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 210,
    render: (row: any) => [
      h(NButton, { text: true, type: 'primary', onClick: () => onView(row) }, () => '方案与实际办理'),
      h(NButton, { text: true, style: 'margin-left:12px', onClick: () => handleTitleClick(row) }, () => '审批进度'),
    ],
  },
];
function onView(row: any) {
  drawerRef.value?.open(row.id);
}
async function create(v: Record<string, any>) {
  const r: any = await samplingApi.create(v);
  await drawerRef.value?.open(r.data);
  return r;
}
function refresh() {
  tableRef.value?.handleRefresh?.();
}
</script>
