<template>
  <n-modal v-model:show="visible" preset="card" :title="modalTitle" style="width: 640px" :mask-closable="false">
    <n-form ref="formRef" :model="form" label-placement="top">
      <SequentialReviewNotice :task="task" />
      <BusinessFormNotice :task="task" />
      <TaskActionNotice :task="task" />

      <!-- 业务表单（自动发现，按 fieldPerms 控制读写） -->
      <template v-if="formComponent">
        <n-divider title-placement="left" style="margin: 0 0 12px">表单信息</n-divider>
        <div style="margin-bottom: 4px">
          <component
            :is="formComponent"
            ref="businessFormRef"
            :form-data="form.formData"
            @update:form-data="form.formData = $event"
            :field-perms="task?.fieldPerms"
            :mode="formMode"
            :task="task"
            :business-type="task?.businessType"
            :business-key="task?.businessKey"
          />
        </div>
      </template>

      <!-- 降级：无表单组件时通用展示（HIDDEN 字段跳过） -->
      <template v-else-if="visibleFields.length > 0">
        <n-divider title-placement="left" style="margin: 0 0 12px">表单信息</n-divider>
        <n-grid :cols="2" :x-gap="12" style="margin-bottom: 4px">
          <n-form-item-gi
            v-for="item in visibleFields"
            :key="item.field"
            :label="item.label || item.field"
          >
            <n-text v-if="item.perm === 'READONLY'">{{ form.formData?.[item.field] ?? '-' }}</n-text>
            <n-input
              v-else-if="typeof (form.formData?.[item.field]) !== 'number'"
              v-model:value="(form.formData as any)[item.field]"
              :placeholder="item.label || item.field"
              :required="item.perm === 'REQUIRED'"
            />
            <n-input-number
              v-else
              v-model:value="(form.formData as any)[item.field]"
              style="width: 100%"
            />
          </n-form-item-gi>
        </n-grid>
      </template>

      <n-divider v-if="formComponent || visibleFields.length > 0" title-placement="left" style="margin: 0 0 12px">
        审批操作
      </n-divider>

      <!-- 操作选择 -->
      <n-form-item label="操作" required v-if="taskActions.length > 1">
        <n-radio-group v-model:value="form.action">
          <n-radio-button
            v-for="action in taskActions"
            :key="action"
            :value="action"
            :type="actionType(action)"
          >
            {{ actionLabel(action) }}
          </n-radio-button>
        </n-radio-group>
      </n-form-item>

      <!-- 转交对象 -->
      <n-form-item v-if="form.action === 'TRANSFER' || form.action === 'DELEGATE'" label="转交给" required>
        <n-select
          v-model:value="form.transferTo"
          filterable
          placeholder="搜索用户"
          :options="userOptions"
          remote
          :loading="userSearching"
          @search="searchUsers"
        />
      </n-form-item>

      <!-- 审批意见 -->
      <n-form-item v-if="shouldShowTaskComment" label="意见">
        <n-input
          v-model:value="form.comment"
          type="textarea"
          :rows="3"
          :placeholder="taskCommentPlaceholder"
        />
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="visible = false">取消</n-button>
        <n-button
          :type="form.action === 'REJECT' ? 'error' : 'primary'"
          :loading="submitting"
          :disabled="!taskActions.includes(form.action)"
          @click="onSubmit"
        >
          {{ actionLabel(form.action, '提交') }}
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import SequentialReviewNotice from './SequentialReviewNotice.vue'
import BusinessFormNotice from './BusinessFormNotice.vue'
import TaskActionNotice from './TaskActionNotice.vue'
import { resolveTaskActions } from '../../utils/taskActions'
import { ref, computed, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { completeTask } from '../../api/task'
import { getFlowForm } from '../../formRegistry'
import { useAssigneeProvider } from '../../composables/useAssigneeProvider'
import { resolveTaskComment } from '../../utils/taskComment'
import type { FlowTask, TaskAction, FieldPermItem, CompleteTaskRequest, FlowFormTaskActionConfig } from '../../typings/flow'

const props = defineProps<{
  task: FlowTask | null
  formData?: Record<string, any>
}>()

const emit = defineEmits<{
  (e: 'success'): void
}>()

const message = useMessage()
const assigneeProvider = useAssigneeProvider()

const visible = defineModel<boolean>('visible', { default: false })

const ACTION_LABELS: Record<string, string> = {
  APPROVE: '同意',
  REJECT: '驳回',
  TRANSFER: '转交',
  DELEGATE: '委托',
  SAVE: '暂存',
}

const form = ref<CompleteTaskRequest>({
  taskId: '',
  action: 'APPROVE',
  comment: '',
  formData: {},
})

const submitting = ref(false)
const userSearching = ref(false)
const userOptions = ref<any[]>([])
const businessFormRef = ref<any>()

// ─── 表单组件自动发现 ──────────────────────────────────────────

/**
 * 业务表单组件解析：formKey（节点级 > 流程级）优先，再退到 businessType。
 * 与后端 DESIGN.md §12 一致。
 */
const formComponent = computed(() => {
  const fk = props.task?.formKey
  const bt = props.task?.businessType
  if (!fk && !bt) return null
  return getFlowForm(fk, bt)
})

/** 审批/经办 模式映射 */
const formMode = computed(() => {
  if (Array.isArray(props.task?.availableActions) && !props.task.availableActions.length) return 'view'
  const type = props.task?.type
  if (type === 'HANDLE') return 'handle'
  return 'approve'
})

// ─── 计算属性 ─────────────────────────────────────────────────

const fieldPerms = computed<FieldPermItem[]>(() => props.task?.fieldPerms || [])

const customTaskActionConfig = computed<FlowFormTaskActionConfig | null>(() => {
  return businessFormRef.value?.getTaskActionConfig?.() ?? null
})
const taskActions = computed<TaskAction[]>(() => resolveTaskActions(props.task, customTaskActionConfig.value))

const actionLabels = computed<Record<string, string>>(() => ({
  ...ACTION_LABELS,
  ...(customTaskActionConfig.value?.labels || {}),
}))
const shouldShowTaskComment = computed(() => taskActions.value.length > 0 && (customTaskActionConfig.value?.showComment ?? true))
const taskCommentPlaceholder = computed(() => {
  const custom = customTaskActionConfig.value?.commentPlaceholder
  if (custom) {
    return custom
  }
  return form.value.action === 'REJECT'
    ? '审批意见（选填，留空自动填写“不同意”）'
    : '审批意见（选填，留空自动填写“同意”）'
})
const requiredCommentActions = computed<TaskAction[]>(() => customTaskActionConfig.value?.requiredCommentActions ?? [])

const modalTitle = computed(() => {
  const typeMap: Record<string, string> = { APPROVE: '审批', HANDLE: '经办', CC: '抄送' }
  return `${props.task?.nodeName || ''} - ${typeMap[props.task?.type || ''] || '处理'}`
})

/** 降级显示：无自定义表单时通用字段列表（过滤 HIDDEN） */
const visibleFields = computed(() => {
  if (formComponent.value) return []
  if (!props.formData) return []
  const permMap = Object.fromEntries(fieldPerms.value.map((p) => [p.field, p]))
  return Object.keys(props.formData)
    .map((key) => ({
      field: key,
      label: permMap[key]?.label || key,
      perm: permMap[key]?.perm || 'READONLY',
    }))
    .filter((item) => item.perm !== 'HIDDEN')
})

// ─── 监听 task 变化 ───────────────────────────────────────────

watch(
  () => props.task,
  (task) => {
    if (!task) return
    form.value = {
      taskId: task.id || '',
      action: (taskActions.value[0] as TaskAction) || 'APPROVE',
      comment: '',
      formData: { ...(props.formData || {}) },
    }
  },
  { immediate: true },
)

watch(
  () => form.value.action,
  (action) => {
    if (action === 'TRANSFER' || action === 'DELEGATE') searchUsers('')
  },
)

// ─── 方法 ─────────────────────────────────────────────────────

function actionType(action: string) {
  const map: Record<string, string> = { REJECT: 'error', APPROVE: 'primary', TRANSFER: 'warning' }
  return map[action] || 'default'
}

function actionLabel(action: string, fallback?: string) {
  return actionLabels.value[action] || fallback || action
}


async function searchUsers(keyword: string) {
  userSearching.value = true
  try {
    userOptions.value = await assigneeProvider.searchUsers(keyword)
  } finally {
    userSearching.value = false
  }
}

async function onSubmit() {
  if (!form.value.taskId || submitting.value || !taskActions.value.includes(form.value.action)) return
  const taskId = form.value.taskId, action = form.value.action
  if (requiredCommentActions.value.includes(form.value.action) && !form.value.comment?.trim()) {
    message.warning('请填写驳回原因')
    return
  }
  if ((form.value.action === 'TRANSFER' || form.value.action === 'DELEGATE') && !form.value.transferTo) {
    message.warning('请选择转交对象')
    return
  }

  // 若有业务表单，先校验并取最新 formData
  if (businessFormRef.value?.validate) {
    try {
      const data = await businessFormRef.value.validate()
      if (props.task?.id !== taskId || form.value.action !== action || !taskActions.value.includes(action)) return
      form.value.formData = data
    } catch {
      return // 表单校验未通过
    }
  }

  submitting.value = true
  try {
    const transferToName = userOptions.value.find((u) => u.value === form.value.transferTo)?.label
    const comment = resolveTaskComment(form.value.action, form.value.comment)
    let closeAfterSuccess = true
    let successMessage = '操作成功'
    if (businessFormRef.value?.runTaskAction) {
      const result = await businessFormRef.value.runTaskAction({
        action: form.value.action,
        task: props.task,
        comment,
        formData: form.value.formData || {},
        transferTo: form.value.transferTo,
        transferToName,
      })
      closeAfterSuccess = result?.close ?? true
      successMessage = result?.successMessage || successMessage
    } else {
      await completeTask({
        ...form.value,
        comment,
        transferToName,
      })
    }
    message.success(successMessage)
    if (closeAfterSuccess) {
      visible.value = false
    }
    emit('success')
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}
</script>
