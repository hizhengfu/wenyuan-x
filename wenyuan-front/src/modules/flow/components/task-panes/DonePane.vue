<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="getDoneList"
    :params="paramsForm"
    :showOperate="false"
    :isAdd="false"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="关键字" path="keyword">
        <n-input v-model:value="paramsForm.keyword" placeholder="流程名称/标题" clearable />
      </n-form-item>
    </template>

    <FlowFormDrawer ref="flowModalRef" />
  </h-page-data-table>
</template>

<script setup lang="ts">
import { ref, h, inject, watch, type Ref } from 'vue';
import { NButton, NSpace } from 'naive-ui';
import { getDoneList, getTaskDetail } from '../../api/task';
import FlowFormDrawer from '../FlowFormDrawer.vue';

const refPageTable = ref<any>(null);
const paramsForm = ref({ keyword: '' });
const flowModalRef = ref<InstanceType<typeof FlowFormDrawer>>();

const refreshSignal = inject<Ref<number> | null>('flowTaskRefresh', null);
if (refreshSignal) {
  watch(refreshSignal, () => refPageTable.value?.handleQuery?.());
}

/** 以只读模式查看已办任务详情 */
async function viewTask(row: any) {
  const res = await getTaskDetail(row.id);
  const task = res.data?.data ?? res.data;
  flowModalRef.value?.openTask(task, true);
}

/** 查看流程实例（含流程图、所有审批记录） */
function viewInstance(row: any) {
  if (!row.instanceId) return;
  flowModalRef.value?.openInstance(row.instanceId, row.instanceTitle);
}

const columns = [
  {
    key: 'instanceTitle',
    title: '标题',
    minWidth: '200px',
    ellipsis: { tooltip: true },
    render: (row: any) =>
      h(NButton, { text: true, type: 'primary', onClick: () => viewTask(row) }, () => row.instanceTitle || '-'),
  },
  { key: 'definitionName', title: '流程名称', width: '150px' },
  {
    key: 'status',
    title: '状态',
    dictType: 'flow_task_status',
    width: '100px',
    align: 'center',
  },
  { key: 'nodeName', title: '节点', width: '120px' },
  {
    key: 'action',
    title: '操作结果',
    width: '100px',
    align: 'center',
    dictType: 'flow_task_action',
  },
  { key: 'comment', title: '意见', minWidth: '150px', ellipsis: { tooltip: true } },
  { key: 'completeTime', title: '处理时间', width: '170px', columnType: 'dateTime' },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    width: '170px',
    align: 'center',
    fixed: 'right',
    render: (row: any) => {
      return h(NButton, { size: 'tiny', type: 'primary', onClick: () => viewTask(row) }, () => '详情');
    },
  },
];
</script>
