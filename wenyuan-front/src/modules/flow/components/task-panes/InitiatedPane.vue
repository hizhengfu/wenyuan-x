<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="myInitiated"
    :params="paramsForm"
    :showOperate="false"
    :isAdd="false"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="关键字" path="keyword">
        <n-input v-model:value="paramsForm.keyword" placeholder="流程标题" clearable />
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-select
          v-model:value="paramsForm.status"
          type='flow_instance_status'
          clearable
          placeholder="全部状态"
        />
      </n-form-item>
    </template>

    <!-- 流程详情 Drawer：进行中时底部显示「撤销」按钮 -->
    <FlowFormDrawer ref="flowModalRef" :cancel-api="cancelInstance" @success="refPageTable?.handleQuery()" />

    <n-modal
      v-model:show="reactivateVisible"
      preset="dialog"
      title="重新激活流程"
      :mask-closable="false"
      style="width: 440px"
    >
      <n-form label-placement="left" label-width="80px">
        <n-form-item label="目标节点" required>
          <n-select
            v-model:value="reactivateForm.targetNodeKey"
            :options="reactivateNodeOptions"
            :loading="reactivateLoadingNodes"
            filterable
            placeholder="选择要跳转的节点"
          />
        </n-form-item>
        <n-form-item label="备注">
          <n-input
            v-model:value="reactivateForm.comment"
            type="textarea"
            :rows="3"
            placeholder="请输入重新激活原因"
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space justify="end">
          <n-button @click="reactivateVisible = false">取消</n-button>
          <n-button type="primary" :loading="reactivateSubmitting" @click="submitReactivate">确认重新激活</n-button>
        </n-space>
      </template>
    </n-modal>
  </h-page-data-table>
</template>

<script setup lang="ts">
import { computed, ref, h, inject, watch, type Ref } from 'vue';
import { useMessage, NButton, NSpace } from 'naive-ui';
import { getDiagramData, getOperationNodes, myInitiated, cancelInstance, reactivateInstance } from '../../api/instance';
import FlowFormDrawer from '../FlowFormDrawer.vue';
import { confirm } from '@/utils';
import { useAuthStore } from '@/store';

const message = useMessage();
const authStore = useAuthStore();
const refPageTable = ref<any>(null);
const paramsForm = ref({ keyword: '', status: null as string | null });
const flowModalRef = ref<InstanceType<typeof FlowFormDrawer>>();
const reactivateVisible = ref(false);
const reactivateLoadingNodes = ref(false);
const reactivateSubmitting = ref(false);
const reactivateRow = ref<any>(null);
const reactivateNodeOptions = ref<Array<{ label: string; value: string }>>([]);
const reactivateForm = ref({
  targetNodeKey: null as string | null,
  comment: '',
});
const canReactivate = computed(() => authStore.hasPermission('ADMIN,FLOW_ALL,FLOW_MANAGE'));

const refreshSignal = inject<Ref<number> | null>('flowTaskRefresh', null);
if (refreshSignal) {
  watch(refreshSignal, () => refPageTable.value?.handleQuery?.());
}

/** 查看流程实例 */
function viewInstance(row: any) {
  flowModalRef.value?.openInstance(row.id, row.title);
}

/** 撤销流程（仅进行中可用） */
async function onCancel(row: any) {
  const diagramRes = await getDiagramData(row.id);
  const diagram = diagramRes.data?.data ?? diagramRes.data;
  if (diagram?.model?.properties?.allowInitiatorCancel === false) {
    message.warning('该流程配置不允许发起人撤销');
    return;
  }
  confirm({
    title: '撤销审批',
    content: `确认撤销「${row.title || '该流程'}」？`,
    type: 'warning',
    onPositiveClick: async () => {
      await cancelInstance(row.id);
      message.success('撤销成功');
      refPageTable.value?.handleQuery();
    },
  });
}

/** 打开重新激活弹窗 */
async function openReactivate(row: any) {
  if (!canReactivate.value) return;
  if (row.status === 'RUNNING') {
    message.warning('运行中的流程不需要重新激活');
    return;
  }
  reactivateRow.value = row;
  reactivateForm.value = { targetNodeKey: null, comment: '' };
  reactivateNodeOptions.value = [];
  reactivateVisible.value = true;
  reactivateLoadingNodes.value = true;
  try {
    const nodesRes = await getOperationNodes(row.id);
    const nodes = nodesRes.data?.data ?? nodesRes.data ?? [];
    reactivateNodeOptions.value = nodes
      .map((node: any) => ({
        label: `${node.name || node.nodeKey}（${node.nodeKey}）`,
        value: node.nodeKey,
      }));
  } catch (e: any) {
    reactivateVisible.value = false;
    message.error(e?.response?.data?.message || e?.message || '加载流程节点失败');
  } finally {
    reactivateLoadingNodes.value = false;
  }
}

/** 提交重新激活 */
async function submitReactivate() {
  const row = reactivateRow.value;
  const targetNodeKey = reactivateForm.value.targetNodeKey;
  if (!row?.id || !targetNodeKey) {
    message.warning('请选择目标节点');
    return;
  }

  confirm({
    title: '重新激活流程',
    content: `确认将「${row.title || '该流程'}」重新激活到所选节点？`,
    type: 'warning',
    onPositiveClick: async () => {
      reactivateSubmitting.value = true;
      try {
        await reactivateInstance(row.id, {
          targetNodeKey,
          comment: reactivateForm.value.comment || undefined,
        });
        message.success('重新激活成功');
        reactivateVisible.value = false;
        refPageTable.value?.handleQuery();
      } finally {
        reactivateSubmitting.value = false;
      }
    },
  });
}

const columns = [
  {
    key: 'title',
    title: '标题',
    minWidth: '200px',
    ellipsis: { tooltip: true },
    render: (row: any) =>
      h(NButton, { text: true, type: 'primary', onClick: () => viewInstance(row) }, () => row.title || '-'),
  },
  { key: 'definitionName', title: '流程', width: '150px' },
  {
    key: 'status',
    title: '状态',
    dictType: 'flow_instance_status',
    width: '100px',
    align: 'center',
  },
  {
    key: 'currentNodeNames',
    title: '当前节点',
    width: '150px',
    render: (row: any) => row.currentNodeNames?.join('、') || '-',
  },
  { key: 'startTime', title: '发起时间', width: '170px', columnType: 'dateTime' },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    width: '240px',
    align: 'center',
    fixed: 'right',
    render: (row: any) =>
      h(NSpace, { size: 4, justify: 'center' }, () =>
        [
          h(NButton, { size: 'tiny', type: 'primary', onClick: () => viewInstance(row) }, () => '查看进度'),
          row.status === 'RUNNING' &&
            h(NButton, { size: 'tiny', type: 'error', onClick: () => onCancel(row) }, () => '撤销'),
          canReactivate.value &&
            row.status !== 'RUNNING' &&
            h(NButton, { size: 'tiny', type: 'warning', onClick: () => openReactivate(row) }, () => '重新激活'),
        ].filter(Boolean),
      ),
  },
];
</script>
