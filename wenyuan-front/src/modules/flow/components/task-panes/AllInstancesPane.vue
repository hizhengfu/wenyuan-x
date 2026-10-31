<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="loadInstances"
    :params="paramsForm"
    :showOperate="false"
    :isAdd="false"
    :columns="columns"
    :enable-selection="canBatchApprove"
    clear-selection-on-fetch
  >
    <template #activeBtn="{ checkedRowKeys, checkedTableData }">
      <n-button
        v-if="canBatchApprove"
        type="primary"
        size="small"
        :disabled="!checkedRowKeys.length"
        :loading="batchPreviewLoading"
        @click="openBatchApproval(checkedRowKeys, checkedTableData)"
      >
        一键强制通过{{ checkedRowKeys.length ? `（${checkedRowKeys.length}）` : '' }}
      </n-button>
    </template>
    <template #queryForm>
      <n-form-item label="关键字" path="keyword">
        <n-input v-model:value="paramsForm.keyword" placeholder="流程标题" clearable />
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-select
          v-model:value="paramsForm.status"
          type="flow_instance_status"
          clearable
          placeholder="全部状态"
        />
      </n-form-item>
      <n-form-item label="流程定义" path="definitionKey">
        <FlowDefinitionSelect
          v-model:value="paramsForm.definitionKey"
          :status="null"
          placeholder="全部流程（含历史版本）"
        />
      </n-form-item>
    </template>

    <FlowFormDrawer ref="flowModalRef" @success="refPageTable?.handleQuery()" />

    <n-modal
      v-model:show="batchVisible"
      preset="dialog"
      title="强制通过并结束流程"
      :mask-closable="!batchSubmitting"
      :closable="!batchSubmitting"
      style="width: min(680px, 94vw)"
    >
      <template v-if="!batchResult">
        <n-alert type="warning" :bordered="false" style="margin-bottom: 12px">
          此操作将跳过剩余审批和必填表单，取消未办任务及运行中的子流程，直接将根流程标记为通过并触发业务完成回调。不会生成逐节点审批记录；请确认业务后果。
        </n-alert>
        <n-space style="margin-bottom: 12px">
          <n-tag type="success">可结束 {{ eligibleCandidates.length }}</n-tag>
          <n-tag v-if="batchCandidates.length > eligibleCandidates.length" type="warning">
            不可操作 {{ batchCandidates.length - eligibleCandidates.length }}
          </n-tag>
        </n-space>
        <div class="batch-approval-list">
          <div v-for="item in batchCandidates" :key="item.instanceId" class="batch-approval-item">
            <div class="batch-approval-title">{{ batchTitles[item.instanceId] || item.instanceId }}</div>
            <div class="batch-approval-detail">
              <n-tag size="small" :type="item.eligible ? 'success' : 'warning'">
                {{ item.eligible ? '可结束' : '跳过' }}
              </n-tag>
              <span v-if="item.reason">{{ item.reason }}</span>
            </div>
          </div>
        </div>
        <n-form style="margin-top: 14px">
          <n-form-item label="强制通过原因" required style="margin-bottom: 0">
            <n-input
              v-model:value="batchComment"
              type="textarea"
              :rows="3"
              :maxlength="500"
              show-count
              placeholder="必填，将记录在流程操作日志中"
            />
          </n-form-item>
        </n-form>
      </template>
      <template v-else>
        <n-alert :type="batchResult.failed ? 'warning' : 'success'" :bordered="false" style="margin-bottom: 12px">
          已强制结束 {{ batchResult.approved }} 条，跳过 {{ batchResult.skipped }} 条，失败 {{ batchResult.failed }} 条。
        </n-alert>
        <div class="batch-approval-list">
          <div v-for="item in batchResult.items" :key="item.instanceId" class="batch-approval-item">
            <div class="batch-approval-title">{{ batchTitles[item.instanceId] || item.instanceId }}</div>
            <div class="batch-approval-detail">
              <n-tag size="small" :type="item.status === 'APPROVED' ? 'success' : 'warning'">
                {{ item.status === 'APPROVED' ? '已结束' : item.status === 'SKIPPED' ? '已跳过' : '失败' }}
              </n-tag>
              <span v-if="item.reason">{{ item.reason }}</span>
            </div>
          </div>
        </div>
      </template>
      <template #action>
        <n-space justify="end">
          <n-button :disabled="batchSubmitting" @click="batchVisible = false">{{ batchResult ? '关闭' : '取消' }}</n-button>
          <n-button
            v-if="!batchResult"
            type="primary"
            :disabled="!eligibleCandidates.length || !batchComment.trim()"
            :loading="batchSubmitting"
            @click="submitBatchApproval"
          >强制结束 {{ eligibleCandidates.length }} 条流程</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="reactivateVisible"
      preset="dialog"
      :title="operationTitle"
      :mask-closable="false"
      style="width: 440px"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 12px">
        跳转只改变人工处理节点；如需直接结束或取消，请使用列表中的独立操作。
      </n-alert>
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
            :placeholder="operationPlaceholder"
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space justify="end">
          <n-button @click="reactivateVisible = false">取消</n-button>
          <n-button type="primary" :loading="reactivateSubmitting" @click="submitNodeOperation">{{ operationConfirmText }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </h-page-data-table>
</template>

<script setup lang="ts">
import { computed, h, inject, ref, watch, type Ref } from 'vue';
import { NButton, NSpace, useMessage } from 'naive-ui';
import { cancelInstance, getOperationNodes, jumpInstance, listInstances, reactivateInstance } from '../../api/instance';
import { batchForceComplete, previewBatchForceComplete, type BatchApprovalCandidate, type BatchApprovalResult } from '../../api/task';
import FlowFormDrawer from '../FlowFormDrawer.vue';
import FlowDefinitionSelect from '../FlowDefinitionSelect.vue';
import { confirm } from '@/utils';
import { useAuthStore } from '@/store';

type NodeOperationMode = 'jump' | 'reactivate';

const message = useMessage();
const authStore = useAuthStore();
const refPageTable = ref<any>(null);
const flowModalRef = ref<InstanceType<typeof FlowFormDrawer>>();
const paramsForm = ref({
  keyword: '',
  status: null as string | null,
  definitionKey: null as string | null,
});

const canReactivate = computed(() => authStore.hasPermission('ADMIN,FLOW_ALL,FLOW_MANAGE'));
const canBatchApprove = computed(() =>
  authStore.hasPermission('ADMIN,FLOW_ALL,FLOW_MANAGE') &&
  authStore.hasPermission('ADMIN,FLOW_ALL,FLOW_TASK_ADMIN_COMPLETE'),
);

const batchVisible = ref(false);
const batchPreviewLoading = ref(false);
const batchSubmitting = ref(false);
const batchCandidates = ref<BatchApprovalCandidate[]>([]);
const batchResult = ref<BatchApprovalResult | null>(null);
const batchComment = ref('');
const batchTitles = ref<Record<string, string>>({});
const eligibleCandidates = computed(() => batchCandidates.value.filter((item) => item.eligible && item.mutationVersion != null));

async function openBatchApproval(ids: string[], rows: any[]) {
  if (!canBatchApprove.value || !ids.length) return;
  if (ids.length > 50) {
    message.warning('每次最多强制结束50条流程');
    return;
  }
  batchPreviewLoading.value = true;
  batchResult.value = null;
  batchComment.value = '';
  batchTitles.value = Object.fromEntries(rows.map((row) => [row.id, row.title || row.id]));
  try {
    const response = await previewBatchForceComplete(ids);
    batchCandidates.value = response.data?.data ?? response.data ?? [];
    batchVisible.value = true;
  } catch (error: any) {
    message.error(error?.response?.data?.message || error?.message || '强制结束预检失败');
  } finally {
    batchPreviewLoading.value = false;
  }
}

async function submitBatchApproval() {
  if (!canBatchApprove.value || !eligibleCandidates.value.length) return;
  if (!batchComment.value.trim()) {
    message.warning('请填写强制通过原因');
    return;
  }
  batchSubmitting.value = true;
  try {
    const items = eligibleCandidates.value.map((item) => ({ instanceId: item.instanceId, mutationVersion: item.mutationVersion! }));
    const response = await batchForceComplete(items, batchComment.value.trim());
    batchResult.value = response.data?.data ?? response.data;
    refPageTable.value?.handleRefresh();
  } catch (error: any) {
    message.error(error?.response?.data?.message || error?.message || '强制结束失败，请核对流程状态');
  } finally {
    batchSubmitting.value = false;
  }
}

const refreshSignal = inject<Ref<number> | null>('flowTaskRefresh', null);
if (refreshSignal) {
  watch(refreshSignal, () => refPageTable.value?.handleQuery?.());
}

async function loadInstances(params: any) {
  const resp = await listInstances({
    keyword: params.keyword || undefined,
    status: params.status || undefined,
    definitionKey: params.definitionKey || undefined,
  });
  const data = resp.data?.data ?? resp.data ?? [];
  const list = Array.isArray(data) ? data : data.list || [];
  const pageSize = Number(params.pageSize || 30);
  const pageNum = Number(params.pageNum || 1);
  const pagedList = pageSize > 0 ? list.slice((pageNum - 1) * pageSize, pageNum * pageSize) : list;
  return {
    data: {
      list: pagedList,
      total: list.length,
      pages: pageSize > 0 ? Math.ceil(list.length / pageSize) : 1,
    },
  };
}

const reactivateVisible = ref(false);
const reactivateLoadingNodes = ref(false);
const reactivateSubmitting = ref(false);
const reactivateRow = ref<any>(null);
const operationMode = ref<NodeOperationMode>('reactivate');
const reactivateNodeOptions = ref<Array<{ label: string; value: string }>>([]);
const reactivateForm = ref({
  targetNodeKey: null as string | null,
  comment: '',
});

const operationTitle = computed(() => (operationMode.value === 'jump' ? '跳转节点' : '重新激活流程'));
const operationConfirmText = computed(() => (operationMode.value === 'jump' ? '确认跳转' : '确认重新激活'));
const operationPlaceholder = computed(() =>
  operationMode.value === 'jump' ? '请输入跳转原因' : '请输入重新激活原因',
);

function viewInstance(row: any) {
  flowModalRef.value?.openInstance(row.id, row.title);
}

async function openNodeOperation(row: any, mode: NodeOperationMode) {
  if (!canReactivate.value) return;
  if (mode === 'reactivate' && row.status === 'RUNNING') {
    message.warning('运行中的流程不需要重新激活');
    return;
  }
  if (mode === 'jump' && row.status !== 'RUNNING') {
    message.warning('只有运行中的流程可以跳转节点');
    return;
  }
  reactivateRow.value = row;
  operationMode.value = mode;
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

async function submitNodeOperation() {
  const row = reactivateRow.value;
  const targetNodeKey = reactivateForm.value.targetNodeKey;
  if (!row?.id || !targetNodeKey) {
    message.warning('请选择目标节点');
    return;
  }

  confirm({
    title: operationTitle.value,
    content:
      operationMode.value === 'jump'
        ? `确认将「${row.title || '该流程'}」跳转到所选节点？`
        : `确认将「${row.title || '该流程'}」重新激活到所选节点？`,
    type: 'warning',
    onPositiveClick: async () => {
      reactivateSubmitting.value = true;
      try {
        const payload = {
          targetNodeKey,
          comment: reactivateForm.value.comment || undefined,
        };
        if (operationMode.value === 'jump') {
          await jumpInstance(row.id, payload);
          message.success('跳转成功');
        } else {
          await reactivateInstance(row.id, payload);
          message.success('重新激活成功');
        }
        reactivateVisible.value = false;
        refPageTable.value?.handleQuery();
      } finally {
        reactivateSubmitting.value = false;
      }
    },
  });
}

function confirmCancelInstance(row: any) {
  if (!canReactivate.value || row.status !== 'RUNNING') return;
  confirm({
    title: '取消流程',
    content: `确认取消「${row.title || '该流程'}」？未办任务及运行中的子流程将撤销，不会产生审批通过的业务结果。`,
    type: 'warning',
    onPositiveClick: async () => {
      await cancelInstance(row.id);
      message.success('流程已取消');
      refPageTable.value?.handleQuery();
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
    key: 'definitionVersion',
    title: '版本',
    width: '80px',
    render: (row: any) => (row.definitionVersion == null ? '-' : `v${row.definitionVersion}`),
  },
  { key: 'initiatorName', title: '发起人', width: '120px' },
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
  { key: 'endTime', title: '结束时间', width: '170px', columnType: 'dateTime' },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    width: '340px',
    align: 'center',
    fixed: 'right',
    render: (row: any) =>
      h(NSpace, { size: 4, justify: 'center' }, () =>
        [
          h(NButton, { size: 'tiny', type: 'primary', onClick: () => viewInstance(row) }, () => '查看进度'),
          canReactivate.value &&
            row.status === 'RUNNING' &&
            h(NButton, { size: 'tiny', type: 'info', onClick: () => openNodeOperation(row, 'jump') }, () => '跳转节点'),
          canBatchApprove.value && !row.parentInstanceId &&
            row.status === 'RUNNING' &&
            h(NButton, { size: 'tiny', type: 'warning', onClick: () => openBatchApproval([row.id], [row]) }, () => '强制通过'),
          canReactivate.value && !row.parentInstanceId &&
            row.status === 'RUNNING' &&
            h(NButton, { size: 'tiny', type: 'error', onClick: () => confirmCancelInstance(row) }, () => '取消流程'),
          canReactivate.value &&
            row.status !== 'RUNNING' &&
            h(NButton, { size: 'tiny', type: 'warning', onClick: () => openNodeOperation(row, 'reactivate') }, () => '重新激活'),
        ].filter(Boolean),
      ),
  },
];
</script>

<style scoped>
.batch-approval-list {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
}

.batch-approval-item {
  padding: 10px 12px;
}

.batch-approval-item + .batch-approval-item {
  border-top: 1px solid var(--n-border-color);
}

.batch-approval-title {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-approval-detail {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
  margin-top: 4px;
  color: var(--n-text-color-3);
  font-size: 12px;
}
</style>
