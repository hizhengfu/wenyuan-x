<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="loadOverdue"
    :params="paramsForm"
    :show-operate="false"
    :isAdd="false"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="范围" path="scope">
        <n-radio-group v-model:value="paramsForm.scope" class="overdue-scope-group">
          <n-radio-button value="mine">我发起的</n-radio-button>
          <n-radio-button value="assigned">我处理的</n-radio-button>
          <n-radio-button v-if="isAdmin" value="all">全部</n-radio-button>
        </n-radio-group>
      </n-form-item>
      <n-form-item label="业务类型" path="businessType">
        <n-input v-model:value="paramsForm.businessType" placeholder="可选，如 PURCHASE_APPLY" clearable />
      </n-form-item>

      <n-form-item v-if="isAdmin">
        <n-button type="warning" :loading="scanLoading" @click="onScan">手动扫描</n-button>
      </n-form-item>
    </template>

    <FlowFormDrawer ref="flowModalRef" />

    <!-- 催办留言弹窗 -->
    <n-modal v-model:show="urgeVisible" preset="card" title="催办" style="width: 480px">
      <n-form :model="urgeForm" label-placement="top">
        <n-form-item label="任务">
          <n-text>{{ urgeTarget?.instanceTitle }} - {{ urgeTarget?.nodeName }}</n-text>
        </n-form-item>
        <n-form-item label="处理人">
          <n-text>{{ urgeTarget?.assigneeName || urgeTarget?.assignee }}</n-text>
        </n-form-item>
        <n-form-item label="逾期">
          <n-tag type="warning">{{ formatOverdue(urgeTarget?.overdueMinutes) }}</n-tag>
        </n-form-item>
        <n-form-item label="留言">
          <n-input
            v-model:value="urgeForm.comment"
            type="textarea"
            :rows="3"
            placeholder="可选，如『请尽快处理，影响项目进度』"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="urgeVisible = false">取消</n-button>
          <n-button type="primary" :loading="urgeLoading" @click="onUrgeConfirm">发送催办</n-button>
        </n-space>
      </template>
    </n-modal>
  </h-page-data-table>
</template>

<script setup lang="ts">
import { ref, h, computed, inject, watch, type Ref } from 'vue'
import { NButton, NTag, NSpace, useMessage } from 'naive-ui'
import { getOverdueTasks, scanTimeoutTasks, urgeTask, getTaskDetail } from '../../api/task'
import type { FlowOverdueTask, OverdueScope } from '../../typings/flow'
import FlowFormDrawer from '../FlowFormDrawer.vue'
import { useAuthStore } from '@/store'

const message = useMessage()
const authStore = useAuthStore()

const refPageTable = ref<any>(null)
const flowModalRef = ref<InstanceType<typeof FlowFormDrawer>>()

const refreshSignal = inject<Ref<number> | null>('flowTaskRefresh', null)
if (refreshSignal) {
  watch(refreshSignal, () => refPageTable.value?.handleQuery?.())
}

const isAdmin = computed(() => authStore.hasPermission('ADMIN,FLOW_ALL,FLOW_MANAGE'))

const currentUserId = computed(() => authStore.userInfo?.id || '')

const paramsForm = ref<{ scope: OverdueScope; businessType: string }>({
  scope: 'mine',
  businessType: '',
})

// ─── 列表加载 ──────────────────────────────────────────────────

function loadOverdue(params: any) {
  return getOverdueTasks({
    scope: params.scope,
    businessType: params.businessType || undefined,
    pageNum: params.pageNum,
    pageSize: params.pageSize,
  })
}

// ─── 手动扫描（仅 ADMIN） ──────────────────────────────────────

const scanLoading = ref(false)
async function onScan() {
  scanLoading.value = true
  try {
    const res = await scanTimeoutTasks()
    const count = res.data?.data ?? 0
    message.success(`扫描完成，本次发布 ${count} 个超时事件`)
    refPageTable.value?.handleQuery()
  } catch (e: any) {
    message.error(e?.message || '扫描失败')
  } finally {
    scanLoading.value = false
  }
}

// ─── 催办 ──────────────────────────────────────────────────────

const urgeVisible = ref(false)
const urgeLoading = ref(false)
const urgeTarget = ref<FlowOverdueTask | null>(null)
const urgeForm = ref<{ comment: string }>({ comment: '' })

function openUrge(row: FlowOverdueTask) {
  urgeTarget.value = row
  urgeForm.value = { comment: '' }
  urgeVisible.value = true
}

async function onUrgeConfirm() {
  if (!urgeTarget.value) return
  urgeLoading.value = true
  try {
    await urgeTask({ taskId: urgeTarget.value.taskId, comment: urgeForm.value.comment || undefined })
    message.success('催办已发送')
    urgeVisible.value = false
    refPageTable.value?.handleQuery()
  } catch (e: any) {
    message.error(e?.message || '催办失败')
  } finally {
    urgeLoading.value = false
  }
}

// ─── 工具 ──────────────────────────────────────────────────────

function formatOverdue(minutes?: number): string {
  if (minutes == null) return '-'
  if (minutes < 60) return `${minutes} 分钟`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`
  const days = Math.floor(minutes / (60 * 24))
  const hours = Math.floor((minutes % (60 * 24)) / 60)
  return `${days} 天 ${hours} 小时`
}

async function openInstance(row: FlowOverdueTask) {
  flowModalRef.value?.openInstance?.(row.instanceId)
}

async function openHandle(row: FlowOverdueTask) {
  const res = await getTaskDetail(row.taskId)
  const task = res.data?.data ?? res.data
  flowModalRef.value?.openTask?.(task, false)
}

/** 当前用户是该任务的处理人 -> 可以开始处理 */
function canHandle(row: FlowOverdueTask): boolean {
  return !!currentUserId.value && row.assignee === currentUserId.value
}

// ─── 表格列 ────────────────────────────────────────────────────

const columns = [
  {
    key: 'instanceTitle',
    title: '标题',
    minWidth: '220px',
    ellipsis: { tooltip: true },
    render: (row: FlowOverdueTask) =>
      h(
        NButton,
        { text: true, type: 'primary', onClick: () => openInstance(row) },
        () => row.instanceTitle || '-',
      ),
  },
  { key: 'definitionName', title: '流程', width: '140px' },
  { key: 'nodeName', title: '节点', width: '120px' },
  {
    key: 'assigneeName',
    title: '处理人',
    width: '120px',
    render: (row: FlowOverdueTask) => row.assigneeName || row.assignee || '-',
  },
  {
    key: 'initiatorName',
    title: '发起人',
    width: '120px',
    render: (row: FlowOverdueTask) => row.initiatorName || row.initiator || '-',
  },
  { key: 'dueTime', title: '到期时间', width: '170px', columnType: 'dateTime' as const },
  {
    key: 'overdueMinutes',
    title: '逾期',
    width: '140px',
    render: (row: FlowOverdueTask) =>
      h(NTag, { type: row.overdueMinutes > 60 * 24 ? 'error' : 'warning', size: 'small' }, () =>
        formatOverdue(row.overdueMinutes),
      ),
  },
  {
    key: 'timeoutNotified',
    title: '已通知',
    width: '90px',
    align: 'center' as const,
    render: (row: FlowOverdueTask) =>
      h(NTag, { size: 'small', type: row.timeoutNotified ? 'success' : 'default' }, () =>
        row.timeoutNotified ? '已发' : '未发',
      ),
  },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    width: '220px',
    fixed: 'right' as const,
    align: 'center' as const,
    render: (row: FlowOverdueTask) =>
      h(NSpace, { size: 4, justify: 'center' }, () => [
        canHandle(row)
          ? h(NButton, { size: 'tiny', type: 'primary', onClick: () => openHandle(row) }, () => '开始处理')
          : h(NButton, { size: 'tiny', onClick: () => openInstance(row) }, () => '详情'),
        h(NButton, { size: 'tiny', type: 'warning', onClick: () => openUrge(row) }, () => '催办'),
      ]),
  },
]
</script>

<style scoped>
:deep(.overdue-scope-group.n-radio-group) {
  width: max-content !important;
  min-width: max-content !important;
}
</style>
