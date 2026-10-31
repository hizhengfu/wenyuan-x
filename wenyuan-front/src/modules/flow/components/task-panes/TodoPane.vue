<template>
  <div class="todo-pane">
    <!-- 显示当前业务范围，关闭标签即可回到更广的待办列表。 -->
    <div v-if="paramsForm.businessType || paramsForm.instanceId" class="todo-pane__filter-bar">
      <n-tag v-if="paramsForm.businessType" round closable type="info" @close="clearBusinessTypeFilter">
        <template #icon>
          <n-icon><HIcon icon="mdi:filter-variant" /></n-icon>
        </template>
        当前模块：{{ businessTypeLabel }}
      </n-tag>
      <n-tag v-if="paramsForm.instanceId" round closable type="info" @close="clearInstanceFilter">当前业务审批</n-tag>
      <span v-if="paramsForm.instanceId">仅显示当前账号可办理的任务；暂无记录表示本账号当前无该业务待办。</span>
    </div>

    <h-page-data-table
      ref="refPageTable"
      :fetch-data="getTodoList"
      :params="paramsForm"
      :isAdd="false"
      :showOperate="false"
      :showHeader="false"
      :columns="columns"
    >
      <template #queryForm>
        <n-form-item label="关键字" path="keyword">
          <n-input v-model:value="paramsForm.keyword" placeholder="流程名称/标题" clearable />
        </n-form-item>
      </template>

      <FlowFormDrawer ref="flowModalRef" @success="refPageTable?.handleQuery()" />
    </h-page-data-table>
  </div>
</template>

<script setup lang="ts">
import { computed, h, inject, onActivated, reactive, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NSpace, useMessage } from 'naive-ui'
import { getTodoList, getTaskDetail } from '../../api/task'
import FlowFormDrawer from '../FlowFormDrawer.vue'
import { buildTaskBusinessRoute } from '../../utils/taskRoute'
import { useAuthStore } from '@/store'
import HIcon from '@/components/basic/DynamicIcon.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const refPageTable = ref<any>(null)
/**
 * 查询参数：keyword 来自表单输入；businessType 来自 URL（"按模块"卡片跳转过来时携带）。
 * 用 reactive 而非 ref，便于响应式跟踪每个字段的变化。
 */
const paramsForm = reactive({
  keyword: '',
  businessType: typeof route.query.businessType === 'string' ? route.query.businessType : '',
  instanceId: typeof route.query.instanceId === 'string' ? route.query.instanceId : '',
})
const flowModalRef = ref<InstanceType<typeof FlowFormDrawer>>()

const businessTypeLabel = computed(
  () => (route.query.businessTypeName as string) || paramsForm.businessType,
)

/** 清除业务类型过滤：跳回不带 businessType 的 todo Tab */
function clearBusinessTypeFilter() {
  const { businessType, businessTypeName, instanceId, ...query } = route.query
  router.replace({ path: route.path, query: { ...query, tab: 'todo' } })
}

function clearInstanceFilter() {
  const { instanceId, ...query } = route.query
  router.replace({ path: route.path, query: { ...query, tab: 'todo' } })
}

// 响应任务中心 Tab 栏的"全局刷新"按钮（独立访问 /flow/task/todo 时无父级 provide，inject 为 null）
const refreshSignal = inject<Ref<number> | null>('flowTaskRefresh', null)
if (refreshSignal) {
  watch(refreshSignal, () => refPageTable.value?.handleQuery?.())
}

onActivated(() => {
  refPageTable.value?.handleQuery?.()
})

// 模块或单据范围变化（业务页面跳转/清除）时重新拉取数据
watch(
  () => [route.query.businessType, route.query.instanceId],
  ([bt, instanceId]) => {
    paramsForm.businessType = typeof bt === 'string' ? bt : ''
    paramsForm.instanceId = typeof instanceId === 'string' ? instanceId : ''
    refPageTable.value?.handleQuery?.()
  },
)

watch(
  () => authStore.userInfo?.id,
  (userId, oldUserId) => {
    if (!userId || !oldUserId || userId === oldUserId) {
      return
    }
    refPageTable.value?.handleQuery?.()
  },
)

/** 处理任务（默认） */
async function handleTask(row: any) {
  const res = await getTaskDetail(row.id)
  const task = res.data?.data ?? res.data
  const currentUserId = authStore.userInfo?.id || ''
  if (task?.status && task.status !== 'PENDING') {
    message.warning('该待办已处理或已失效，正在刷新列表')
    refPageTable.value?.handleQuery?.()
    return
  }
  if (
    currentUserId &&
    task?.type !== 'CC' &&
    task?.assignee &&
    task.assignee !== currentUserId
  ) {
    message.warning('该待办已不属于当前账号，正在刷新列表')
    refPageTable.value?.handleQuery?.()
    return
  }
  const targetRoute = buildTaskBusinessRoute(task)
  if (targetRoute) {
    router.push(targetRoute)
    return
  }
  flowModalRef.value?.openTask(task, false)
}

const columns = [
  {
    key: 'instanceTitle',
    title: '标题',
    minWidth: '200px',
    ellipsis: { tooltip: true },
    render: (row: any) =>
      h(NButton, { text: true, type: 'primary', onClick: () => handleTask(row) }, () => row.instanceTitle || '-'),
  },
  { key: 'definitionName', title: '流程名称', width: '150px' },
  { key: 'nodeName', title: '节点', width: '120px' },
  { key: 'dueTime', title: '到期时间', width: '170px', columnType: 'dateTime' },
  { key: 'createTime', title: '发起时间', width: '170px', columnType: 'dateTime' },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    width: '170px',
    align: 'center',
    fixed: 'right',
    render: (row: any) =>
      h(NSpace, { size: 4, justify: 'center' }, () => [
        h(NButton, { size: 'tiny', type: 'primary', onClick: () => handleTask(row) }, () => '去处理'),
      ]),
  },
]
</script>

<style scoped lang="scss">
.todo-pane {
  flex: 1;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.pageTable) {
    flex: 1;
    min-height: 0;
    height: 100%;
  }

  &__filter-bar {
    flex-shrink: 0;
    padding: 8px 12px 0;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
