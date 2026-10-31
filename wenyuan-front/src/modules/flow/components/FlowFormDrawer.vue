<template>
  <!-- 主 Drawer -->
  <n-drawer
    v-model:show="visible"
    :width="drawerWidth"
    placement="right"
    :trap-focus="false"
    :content-style="{ height: '100%', overflow: 'hidden' }"
  >
    <n-drawer-content
      class="ffd-drawer-content"
      :native-scrollbar="false"
      :body-content-style="{
        padding: 0,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        flex: '1',
        height: '100%',
        minHeight: 0,
      }"
    >
      <!-- 标题栏 -->
      <template #header>
        <div style="display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0">
          <span
            style="font-size: 15px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis"
          >
            {{ titleText }}
          </span>
          <FlowStatusTag v-if="flowStatus" :status="flowStatus" />
        </div>
      </template>

      <n-alert v-if="loadError" type="error" style="margin: 8px 16px">{{ loadError }}</n-alert>
      <SequentialReviewNotice v-if="!loading" :task="currentTask" style="margin: 8px 16px; flex-shrink: 0" />
      <BusinessFormNotice v-if="!loading" :task="currentTask" style="margin: 8px 16px; flex-shrink: 0" />
      <TaskActionNotice v-if="!loading" :task="currentTask" style="margin: 8px 16px; flex-shrink: 0" />

      <!-- 主体：左侧表单 + 右侧 Tab 面板 -->
      <div class="ffd-main">
        <!-- 左侧：业务表单 -->
        <div class="ffd-left" :class="{ 'ffd-left--fixed-layout': useFixedSideLayout }">
          <div v-if="useFixedSideLayout" class="ffd-left-fixed-shell">
            <div class="ffd-left-fixed-body">
              <n-spin :show="loading" description="加载中...">
                <template v-if="!loading">
                  <component
                    v-if="formComponent"
                    :is="formComponent"
                    ref="businessFormRef"
                    :form-data="innerFormData"
                    @update:form-data="innerFormData = $event"
                    :field-perms="resolvedFieldPerms"
                    :mode="resolvedFormMode"
                    :task="currentTask"
                    :business-type="businessType"
                    :business-key="currentBusinessKey"
                  />
                  <n-descriptions v-else-if="Object.keys(innerFormData).length" bordered :column="2" size="small">
                    <n-descriptions-item v-for="(val, key) in innerFormData" :key="key" :label="String(key)">
                      {{ val ?? '-' }}
                    </n-descriptions-item>
                  </n-descriptions>
                  <n-empty v-else description="暂无表单数据" style="padding: 60px 0" />
                </template>
              </n-spin>
            </div>
          </div>
          <n-scrollbar v-else class="ffd-left-scroll">
            <div class="ffd-left-scroll-body">
              <n-spin :show="loading" description="加载中...">
                <template v-if="!loading">
                  <component
                    v-if="formComponent"
                    :is="formComponent"
                    ref="businessFormRef"
                    :form-data="innerFormData"
                    @update:form-data="innerFormData = $event"
                    :field-perms="resolvedFieldPerms"
                    :mode="resolvedFormMode"
                    :task="currentTask"
                    :business-type="businessType"
                    :business-key="currentBusinessKey"
                  />
                  <n-descriptions v-else-if="Object.keys(innerFormData).length" bordered :column="2" size="small">
                    <n-descriptions-item v-for="(val, key) in innerFormData" :key="key" :label="String(key)">
                      {{ val ?? '-' }}
                    </n-descriptions-item>
                  </n-descriptions>
                  <n-empty v-else description="暂无表单数据" style="padding: 60px 0" />
                </template>
              </n-spin>
            </div>
          </n-scrollbar>
        </div>
        <!-- 右侧：Tab 面板（支持折叠） -->
        <div v-if="resolvedShowFlowPanel" class="ffd-right" :class="{ 'ffd-right--collapsed': rightPanelCollapsed }">
          <!-- ① 折叠状态：垂直 tab 条 -->
          <template v-if="rightPanelCollapsed">
            <div class="ffd-vtabs">
              <div
                v-for="t in tabList"
                :key="t.name"
                class="ffd-vtab"
                :class="{ 'ffd-vtab--active': activeTab === t.name }"
                @click="expandPanel(t.name)"
              >
                <span>{{ t.label }}</span>
              </div>
            </div>
          </template>

          <!-- ② 展开状态：水平 tabs + 内容 -->
          <template v-else>
            <!-- Tab 栏 + 折叠按钮 -->
            <div class="ffd-tabs-bar">
              <n-tabs v-model:value="activeTab" type="line" size="small" class="ffd-tabs">
                <n-tab v-for="t in tabList" :key="t.name" :name="t.name">{{ t.label }}</n-tab>
              </n-tabs>
              <n-tooltip placement="left" trigger="hover">
                <template #trigger>
                  <n-button text size="small" class="ffd-collapse-btn" @click="rightPanelCollapsed = true">
                    <template #icon>
                      <NIcon size="24">
                        <HIcon icon="c:ChevronDoubleRight" />
                      </NIcon>
                    </template>
                  </n-button>
                </template>
                折叠面板
              </n-tooltip>
            </div>

            <div v-if="activeTab === 'flow'" class="ffd-flow-toolbar">
              <n-button size="tiny" @click="detailVisible = true" :disabled="!diagramData && !logs.length">
                流程详情
              </n-button>
            </div>
            <n-scrollbar class="ffd-tab-body">
              <div style="padding: 12px 14px">
                <!-- 流程日志：Timeline 展示 -->
                <template v-if="activeTab === 'flow'">
                  <n-spin :show="loading" size="small">
                    <TaskHistoryTimeline :logs="logs" />
                  </n-spin>
                </template>
              </div>
            </n-scrollbar>
          </template>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <template #footer>
        <!-- 查看/编辑模式 -->
        <template v-if="internalMode === 'view'">
          <n-space>
            <n-button @click="visible = false" type="warning">
              <template #icon>
                <HIcon icon="m:flow-close" />
              </template>
              关闭
            </n-button>
            <!-- 草稿：表单可编辑，底部显示保存草稿 + 提交审批 -->
            <template v-if="canEditStatus">
              <n-button v-if="canSaveDraft" :loading="actionLoading === 'save'" @click="handleSave">
                <template #icon>
                  <HIcon icon="m:flow-save" />
                </template>
                保存草稿</n-button
              >
              <n-button
                v-if="canSubmitDraft"
                type="primary"
                :loading="actionLoading === 'submit'"
                @click="handleSubmit"
              >
                <template #icon>
                  <HIcon icon="m:flow-node-approve" />
                </template>

                提交审批</n-button
              >
            </template>
            <!-- 审批中：只读 + 撤销 -->
            <n-button
              v-if="!businessReadOnly && flowStatus === 'AUDIT' && props.cancelApi && flowAllowsInitiatorCancel"
              type="error"
              :loading="actionLoading === 'cancel'"
              @click="handleCancel"
            >
              <template #icon>
                <HIcon icon="m:flow-undo" />
              </template>
              撤销审批</n-button
            >
            <!-- 审批中 / 驳回 / 撤销：先作废，再允许删除 -->
            <n-button
              v-if="canVoidStatus && props.voidApi"
              type="error"
              :loading="actionLoading === 'void'"
              @click="handleVoid"
            >
              <template #icon>
                <HIcon icon="m:flow-close" />
              </template>
              作废</n-button
            >
            <n-button
              v-if="canDeleteStatus && props.deleteApi"
              type="error"
              :loading="actionLoading === 'delete'"
              @click="handleDelete"
            >
              <template #icon>
                <HIcon icon="m:flow-close" />
              </template>
              删除</n-button
            >
          </n-space>
        </template>

        <!-- 任务模式：审批意见 + 操作按钮 -->
        <template v-else>
          <n-flex style="width: 100%" vertical>
            <n-input
              v-if="shouldShowTaskComment"
              v-model:value="actionForm.comment"
              type="textarea"
              :rows="3"
              :placeholder="taskCommentPlaceholder"
              style="flex: 1"
            />
            <n-flex justify="end">
              <n-button @click="visible = false" type="warning">
                <template #icon>
                  <HIcon icon="m:flow-close" />
                </template>
                {{ taskCloseLabel }}
              </n-button>
              <n-button
                v-if="hasTaskDraftSaver ? canSaveTaskDraft : hasAction('SAVE')"
                :loading="submitting && actionForm.pendingAction === 'SAVE'"
                @click="hasTaskDraftSaver ? handleSaveTaskDraft() : doAction('SAVE')"
              >
                <template #icon>
                  <HIcon icon="m:flow-save" />
                </template>
                {{ actionLabel('SAVE') }}</n-button
              >
              <n-button v-if="hasAction('TRANSFER')" @click="openTransferDialog">
                <template #icon>
                  <HIcon icon="m:flow-node-cc" />
                </template>
                {{ actionLabel('TRANSFER') }}</n-button
              >
              <n-button
                v-if="hasAction('REJECT')"
                type="error"
                :loading="submitting && actionForm.pendingAction === 'REJECT'"
                @click="doAction('REJECT')"
              >
                <template #icon>
                  <HIcon icon="m:flow-close" />
                </template>
                {{ actionLabel('REJECT') }}</n-button
              >
              <n-button
                v-if="hasAction('APPROVE')"
                type="primary"
                :loading="submitting && actionForm.pendingAction === 'APPROVE'"
                @click="doAction('APPROVE')"
              >
                <template #icon>
                  <HIcon icon="m:flow-node-approve" />
                </template>
                {{ actionLabel('APPROVE', currentTask?.type === 'HANDLE' ? '提交' : '同意') }}
              </n-button>
            </n-flex>
          </n-flex>
        </template>
      </template>
    </n-drawer-content>
  </n-drawer>

  <!-- 流程详情抽屉：流程图 + 日志表格 -->
  <DrawerPageForm
    v-model:show="detailVisible"
    title="流程详情"
    width="72%"
    :enable-cancel="false"
    :content-style="{ height: '100%', overflow: 'hidden' }"
    class="ffd-detail-drawer"
  >
    <n-scrollbar class="ffd-detail-scroll">
      <div class="ffd-detail-body">
        <n-tabs type="line" size="small" class="ffd-detail-tabs">
          <n-tab-pane name="diagram" tab="流程图">
            <div class="ffd-detail-diagram">
              <FlowViewer v-if="diagramData" :diagram-data="diagramData" />
              <n-empty v-else description="暂无流程图" class="ffd-detail-empty" />
            </div>
          </n-tab-pane>
          <n-tab-pane name="history" tab="审批记录">
            <TaskHistory :logs="logs" />
          </n-tab-pane>
        </n-tabs>
      </div>
    </n-scrollbar>
    <template #form-action>
      <n-space justify="end">
        <n-button @click="detailVisible = false">关闭</n-button>
        <n-button v-if="canEditFlowDefinition" type="primary" secondary @click="openDefinitionDesigner">
          <template #icon>
            <HIcon icon="mdi:pencil-outline" />
          </template>
          编辑流程
        </n-button>
        <n-button
          v-if="canJumpInstance"
          type="info"
          :loading="nodeOperationLoading && nodeOperationMode === 'jump'"
          @click="openNodeOperation('jump')"
        >
          <template #icon>
            <HIcon icon="m:flow-node-approve" />
          </template>
          跳转节点
        </n-button>
        <n-button v-if="canForceCompleteInstance" type="warning" @click="openForceComplete">
          强制通过
        </n-button>
        <n-button v-if="canCancelRunningInstance" type="error" secondary @click="confirmCancelRunningInstance">
          取消流程
        </n-button>
        <n-button
          v-if="canReactivateInstance"
          type="warning"
          :loading="nodeOperationLoading && nodeOperationMode === 'reactivate'"
          @click="openNodeOperation('reactivate')"
        >
          <template #icon>
            <HIcon icon="m:flow-undo" />
          </template>
          重新激活
        </n-button>
      </n-space>
    </template>
  </DrawerPageForm>

  <!-- 流程运维：跳转节点 / 重新激活 -->
  <n-modal
    v-model:show="nodeOperationVisible"
    preset="dialog"
    :title="nodeOperationTitle"
    :mask-closable="false"
    style="width: 440px"
  >
    <n-alert type="info" :bordered="false" style="margin-bottom: 12px">
      跳转只改变人工处理节点；直接结束或取消请使用流程详情中的独立操作。
    </n-alert>
    <n-form label-placement="left" label-width="80px">
      <n-form-item label="目标节点" required>
        <n-select
          v-model:value="nodeOperationForm.targetNodeKey"
          :options="nodeOperationOptions"
          :loading="nodeOperationLoading"
          filterable
          placeholder="选择目标节点"
        />
      </n-form-item>
      <n-form-item label="备注">
        <n-input
          v-model:value="nodeOperationForm.comment"
          type="textarea"
          :rows="3"
          :placeholder="nodeOperationPlaceholder"
        />
      </n-form-item>
    </n-form>
    <template #action>
      <n-space justify="end">
        <n-button @click="nodeOperationVisible = false">取消</n-button>
        <n-button type="primary" :loading="nodeOperationSubmitting" @click="submitNodeOperation">
          {{ nodeOperationConfirmText }}
        </n-button>
      </n-space>
    </template>
  </n-modal>

  <n-modal v-model:show="forceCompleteVisible" preset="dialog" title="强制通过并结束流程" style="width: 480px" :mask-closable="!forceCompleteSubmitting" :closable="!forceCompleteSubmitting">
    <n-alert type="warning" :bordered="false" style="margin-bottom: 12px">
      将跳过剩余审批和必填表单，取消未办任务及运行中子流程，并触发业务完成回调；不会生成逐节点审批记录。
    </n-alert>
    <n-form-item label="强制通过原因" required>
      <n-input v-model:value="forceCompleteReason" type="textarea" :rows="3" :maxlength="500" show-count placeholder="请填写原因，记录在流程操作日志中" />
    </n-form-item>
    <template #action>
      <n-space justify="end">
        <n-button :disabled="forceCompleteSubmitting" @click="forceCompleteVisible = false">取消</n-button>
        <n-button type="warning" :loading="forceCompleteSubmitting" :disabled="!forceCompleteReason.trim()" @click="submitForceComplete">确认强制通过</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- 转交用户选择弹窗 -->
  <n-modal v-model:show="transferVisible" preset="dialog" title="转交给" :mask-closable="false" style="width: 380px">
    <n-select
      v-model:value="actionForm.transferTo"
      filterable
      placeholder="搜索用户"
      :options="userOptions"
      remote
      :loading="userSearching"
      @search="searchUsers"
      style="margin-top: 8px"
    />
    <template #action>
      <n-space justify="end">
        <n-button @click="transferVisible = false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="doAction('TRANSFER')">确认转交</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount, onDeactivated } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, useDialog } from 'naive-ui';
import FlowViewer from './viewer/FlowViewer.vue';
import FlowStatusTag from './viewer/FlowStatusTag.vue';
import TaskHistory from './task/TaskHistory.vue';
import SequentialReviewNotice from './task/SequentialReviewNotice.vue';
import BusinessFormNotice from './task/BusinessFormNotice.vue';
import TaskActionNotice from './task/TaskActionNotice.vue';
import { resolveTaskActions } from '../utils/taskActions';
import TaskHistoryTimeline from './task/TaskHistoryTimeline.vue';
import DrawerPageForm from '@/components/form/form/DrawerPageForm.vue';
import { getFlowForm } from '../formRegistry';
import { useAssigneeProvider } from '../composables/useAssigneeProvider';
import {
  getInstance,
  getDiagramData,
  getHistory,
  getOperationNodes,
  cancelInstance,
  jumpInstance,
  reactivateInstance,
} from '../api/instance';
import { batchForceComplete, completeTask, previewBatchForceComplete } from '../api/task';
import { resolveTaskComment } from '../utils/taskComment';
import type {
  FlowTask,
  TaskAction,
  FlowDiagramData,
  FlowInstance,
  FlowInstanceLog,
  FlowFormTaskActionConfig,
  FlowOperationNode,
} from '../typings/flow';
import HIcon from '@/components/basic/DynamicIcon.vue';
import { useAuthStore } from '@/store';

// ─── Props & Emits ────────────────────────────────────────────
const props = defineProps<{
  /** view 模式下的数据拉取函数（如 getFlowInfo） */
  fetchFn?: (id: string) => Promise<any>;
  /** view 模式：提交审批接口 */
  submitApi?: (id: string) => Promise<any>;
  /** view 模式：撤销审批接口 */
  cancelApi?: (id: string) => Promise<any>;
  /** view 模式：作废接口 */
  voidApi?: (id: string) => Promise<any>;
  /** view 模式：删除接口 */
  deleteApi?: (id: string) => Promise<any>;
  /** view 模式：新建草稿接口 */
  createApi?: (data: Record<string, any>) => Promise<any>;
  /** view 模式：保存编辑接口（传入则支持内联编辑） */
  saveApi?: (id: string, data: Record<string, any>) => Promise<any>;
}>();

const emit = defineEmits<{
  (e: 'success'): void;
}>();

const message = useMessage();
const dialog = useDialog();
const assigneeProvider = useAssigneeProvider();
const router = useRouter();
const authStore = useAuthStore();
const flowManagePermission = 'ADMIN,FLOW_ALL,FLOW_MANAGE';

// ─── 状态 ─────────────────────────────────────────────────────
const visible = ref(false);
const loading = ref(false);
const loadError = ref('');
let detailRevision = 0;
let taskDraftCommentRestored = false;
let userSearchRevision = 0;
const internalMode = ref<'view' | 'task'>('view');
/** 只读业务入口只使用已授权的业务摘要，不取得通用实例或办理能力。 */
const businessReadOnly = ref(false);
let businessReadAuthContext = '';
/** view 模式下当前记录 ID，用于提交/撤销/作废操作 */
const currentRecordId = ref<string>('');
/** view 模式操作 loading 标记 */
const actionLoading = ref<'submit' | 'cancel' | 'void' | 'save' | 'delete' | null>(null);
const titleText = ref('审批详情');
const flowStatus = ref<string | undefined>();
const innerFormData = ref<Record<string, any>>({});
const businessType = ref<string | undefined>();
const diagramData = ref<FlowDiagramData | null>(null);
const logs = ref<FlowInstanceLog[]>([]);
const currentTask = ref<FlowTask | null>(null);
const currentInstance = ref<FlowInstance | null>(null);
const businessKey = ref<string | undefined>();
const activeTab = ref<'flow' | 'comment' | 'reminder'>('flow');
const rightPanelCollapsed = ref(false);

/** 可用 tab 列表（注释掉的 tab 不出现在垂直条中） */
const tabList = [
  { name: 'flow' as const, label: '流程日志' },
  // { name: 'comment' as const, label: '评论' },
  // { name: 'reminder' as const, label: '任务提醒' },
];
const businessFormRef = ref<any>();
const submitting = ref(false);
const detailVisible = ref(false);
const transferVisible = ref(false);
const userSearching = ref(false);
const userOptions = ref<any[]>([]);
type NodeOperationMode = 'jump' | 'reactivate';
const nodeOperationVisible = ref(false);
const nodeOperationLoading = ref(false);
const nodeOperationSubmitting = ref(false);
const nodeOperationMode = ref<NodeOperationMode>('jump');
const nodeOperationOptions = ref<Array<{ label: string; value: string }>>([]);
const nodeOperationForm = ref({
  targetNodeKey: null as string | null,
  comment: '',
});
const forceCompleteVisible = ref(false);
const forceCompleteSubmitting = ref(false);
const forceCompleteReason = ref('');
const forceCompleteVersion = ref<number | null>(null);

const actionForm = ref({
  comment: '',
  pendingAction: 'APPROVE' as TaskAction,
  transferTo: undefined as string | undefined,
});
const DEFAULT_ACTION_LABELS: Record<TaskAction, string> = {
  APPROVE: '同意',
  REJECT: '驳回',
  TRANSFER: '转交',
  DELEGATE: '委托',
  SAVE: '暂存',
};

// ─── 计算属性 ─────────────────────────────────────────────────

const formComponent = computed(() => {
  // 优先使用任务的 formKey（节点级 > 流程级，由后端解析），无 formKey 时回退 businessType
  const fk = currentTask.value?.formKey;
  const bt = businessType.value;
  if (!fk && !bt) return null;
  return getFlowForm(fk, bt);
});

const resolvedFormMode = computed(() => {
  if (internalMode.value === 'view') {
    // 草稿状态直接可编辑，其余只读
    return canEditStatus.value ? ('handle' as const) : ('view' as const);
  }
  if (Array.isArray(currentTask.value?.availableActions) && !currentTask.value.availableActions.length)
    return 'view' as const;
  const type = currentTask.value?.type;
  return type === 'HANDLE' ? ('handle' as const) : ('approve' as const);
});

/** 允许编辑/提交的状态：必须明确为草稿，状态未知时视为只读 */
const canEditStatus = computed(() => !businessReadOnly.value && !loading.value && !loadError.value && flowStatus.value === 'DRAFT');
const canDeleteStatus = computed(
  () => !businessReadOnly.value && !loading.value && Boolean(currentRecordId.value) && ['DRAFT', 'VOID'].includes(flowStatus.value || ''),
);
const canVoidStatus = computed(() => !businessReadOnly.value && !loading.value && ['AUDIT', 'REJECT', 'CANCEL'].includes(flowStatus.value || ''));
const canSaveDraft = computed(
  () => canEditStatus.value && (currentRecordId.value ? Boolean(props.saveApi) : Boolean(props.createApi)),
);
const canSubmitDraft = computed(
  () => canEditStatus.value && Boolean(props.submitApi) && Boolean(currentRecordId.value || props.createApi),
);

const resolvedFieldPerms = computed(() => currentTask.value?.fieldPerms);
const flowAllowsInitiatorCancel = computed(() => diagramData.value?.model?.properties?.allowInitiatorCancel !== false);

const normalizedActions = computed<TaskAction[]>(() => resolveTaskActions(currentTask.value));

const customTaskActionConfig = computed<FlowFormTaskActionConfig | null>(() => {
  return businessFormRef.value?.getTaskActionConfig?.() ?? null;
});
const taskActions = computed<TaskAction[]>(() =>
  businessReadOnly.value || loading.value || loadError.value ? [] : resolveTaskActions(currentTask.value, customTaskActionConfig.value),
);

/** 业务草稿保存独立于流程动作，不扩大服务端返回的可办理范围。 */
const hasTaskDraftSaver = computed(
  () => customTaskActionConfig.value?.allowSaveDraft !== undefined && typeof businessFormRef.value?.saveTaskDraft === 'function',
);
const canSaveTaskDraft = computed(
  () =>
    visible.value &&
    internalMode.value === 'task' &&
    !businessReadOnly.value &&
    !loading.value &&
    !loadError.value &&
    Boolean(currentTask.value?.id) &&
    currentTask.value?.status === 'PENDING' &&
    resolvedFormMode.value !== 'view' &&
    normalizedActions.value.some((action) => action === 'APPROVE' || action === 'SAVE') &&
    customTaskActionConfig.value?.allowSaveDraft === true &&
    hasTaskDraftSaver.value,
);

// 仅启用业务暂存的任务回填已存意见；每次打开只处理一次，不覆盖后续编辑。
watch([hasTaskDraftSaver, currentTask], ([usesBusinessDraft, task]) => {
  if (!usesBusinessDraft || taskDraftCommentRestored || internalMode.value !== 'task' || task?.status !== 'PENDING') return;
  if (!actionForm.value.comment) actionForm.value.comment = task.comment || '';
  taskDraftCommentRestored = true;
});

const taskActionLabels = computed<Record<string, string>>(() => ({
  ...DEFAULT_ACTION_LABELS,
  ...(customTaskActionConfig.value?.labels || {}),
}));
const shouldShowTaskComment = computed(
  () => taskActions.value.length > 0 && (customTaskActionConfig.value?.showComment ?? true),
);
const taskCommentPlaceholder = computed(() => {
  const customPlaceholder = customTaskActionConfig.value?.commentPlaceholder;
  if (customPlaceholder) {
    return customPlaceholder;
  }
  return actionForm.value.pendingAction === 'REJECT'
    ? '审批意见（选填，留空自动填写“不同意”）'
    : '审批意见（选填，留空自动填写“同意”）';
});
const requiredCommentActions = computed<TaskAction[]>(() => customTaskActionConfig.value?.requiredCommentActions ?? []);
const taskCloseLabel = computed(() => customTaskActionConfig.value?.closeLabel || '关闭');
const useFixedSideLayout = computed(() => {
  const business = businessType.value?.toUpperCase?.() || '';
  return business === 'SATELLITE_REMOTE_CHECK' || currentTask.value?.formKey === '/satellite/remote/matter';
});
const drawerWidth = computed(() => {
  if (useFixedSideLayout.value) {
    return '92%';
  }
  return Math.min(1200, window.innerWidth * 0.9);
});
const currentBusinessKey = computed(
  () => currentTask.value?.businessKey || businessKey.value || currentRecordId.value || undefined,
);
const isNewRecordMode = computed(
  () =>
    !currentRecordId.value &&
    !currentTask.value?.id &&
    !(currentTask.value && resolveTaskInstanceId(currentTask.value)) &&
    !currentInstance.value?.id,
);
const resolvedShowFlowPanel = computed(() => !isNewRecordMode.value);
const currentDefinitionId = computed(
  () => currentInstance.value?.definitionId || currentTask.value?.definitionId || '',
);
const currentDefinitionTitle = computed(
  () => currentInstance.value?.definitionName || currentTask.value?.definitionName || titleText.value || '流程设计',
);
const canManageFlowDefinition = computed(
  () => !businessReadOnly.value && (currentInstance.value?.canManage === true || authStore.hasPermission(flowManagePermission)),
);
const canEditFlowDefinition = computed(() => canManageFlowDefinition.value && Boolean(currentDefinitionId.value));
const canJumpInstance = computed(() => !businessReadOnly.value && currentInstance.value?.canJump === true);
const canCancelRunningInstance = computed(() => canJumpInstance.value && !currentInstance.value?.parentInstanceId);
const canForceCompleteInstance = computed(() =>
  canCancelRunningInstance.value && authStore.hasPermission('ADMIN,FLOW_ALL,FLOW_TASK_ADMIN_COMPLETE'),
);
const canReactivateInstance = computed(() => !businessReadOnly.value && currentInstance.value?.canReactivate === true);
const nodeOperationTitle = computed(() => (nodeOperationMode.value === 'jump' ? '跳转节点' : '重新激活流程'));
const nodeOperationConfirmText = computed(() => (nodeOperationMode.value === 'jump' ? '确认跳转' : '确认重新激活'));
const nodeOperationPlaceholder = computed(() =>
  nodeOperationMode.value === 'jump' ? '请输入跳转原因' : '请输入重新激活原因',
);

const INSTANCE_STATUS_MAP: Record<string, string> = {
  RUNNING: 'AUDIT',
  COMPLETED: 'PASS',
  CANCELED: 'CANCEL',
  REJECTED: 'REJECT',
};

// ─── 公开方法 ─────────────────────────────────────────────────

/**
 * 查看模式打开（兼容 useFlowApproval 的 drawerRef.open(id, title) 调用）
 */
async function open(id: string, title?: string) {
  return openBusiness(id, title);
}

async function openBusinessReadOnly(id: string, title: string, expectedBusinessType: string) {
  return openBusiness(id, title, expectedBusinessType);
}

async function openBusiness(id: string, title?: string, readOnlyBusinessType?: string) {
  reset();
  businessReadOnly.value = readOnlyBusinessType !== undefined;
  businessReadAuthContext = businessReadOnly.value ? currentBusinessReadAuthContext() : '';
  const revision = detailRevision;
  currentRecordId.value = id;
  internalMode.value = 'view';
  titleText.value = title ?? '审批详情';
  visible.value = true;
  loading.value = true;
  try {
    if (!props.fetchFn || !id) throw new Error('缺少可读取的流程记录');
    if (businessReadOnly.value && (!authStore.userInfo?.id || !readOnlyBusinessType?.trim()))
      throw new Error('请登录后从对应业务案卷查看审批资料');
    const resp = await props.fetchFn(id);
    if (!isCurrentDetail(revision)) return;
    const data = resp?.data?.data ?? resp?.data;
    if (
      !data ||
      typeof data !== 'object' ||
      Array.isArray(data) ||
      !data.businessType ||
      typeof data.businessType !== 'string' ||
      (data.businessKey && data.businessKey !== id) ||
      (businessReadOnly.value && (data.businessKey !== id || data.businessType !== readOnlyBusinessType))
    )
      throw new Error('流程资料缺失或与当前记录不一致，请重新核对');
    const instance = !businessReadOnly.value && data.flowInstanceId ? await readInstance(data.flowInstanceId) : null;
    if (!isCurrentDetail(revision)) return;
    flowStatus.value = data.flowStatus;
    innerFormData.value = data.formData || {};
    businessKey.value = data.businessKey || id;
    businessType.value = data.businessType;
    diagramData.value = data.diagram;
    logs.value = data.history || [];
    if (instance) applyInstanceState(instance);
  } catch (error) {
    failDetailRead(revision, error);
  } finally {
    if (isCurrentDetail(revision)) loading.value = false;
  }
}

/**
 * 任务模式打开（todo.vue 的「处理」/「详情」按钮）
 */
async function openTask(task: FlowTask, viewOnly = false) {
  reset();
  const revision = detailRevision;
  internalMode.value = viewOnly ? 'view' : 'task';
  titleText.value = [task.nodeName, task.instanceTitle].filter(Boolean).join(' - ') || '任务处理';
  visible.value = true;
  loading.value = true;
  try {
    const instanceId = resolveTaskInstanceId(task);
    if (!instanceId) throw new Error('任务缺少流程实例，无法核实当前资料');
    const [inst, diagramRes, historyRes] = await Promise.all([
      readInstance(instanceId),
      getDiagramData(instanceId),
      getHistory(instanceId),
    ]);
    if (!isCurrentDetail(revision)) return;
    if (task.businessKey && inst.businessKey && task.businessKey !== inst.businessKey)
      throw new Error('任务与流程业务记录不一致，请重新核对');
    currentTask.value = task;
    innerFormData.value = { ...(task.formData || {}) };
    businessKey.value = task.businessKey;
    businessType.value = task.businessType;
    actionForm.value.pendingAction = normalizedActions.value[0] || 'APPROVE';
    applyInstanceState(inst);
    diagramData.value = diagramRes.data?.data ?? diagramRes.data;
    logs.value = historyRes.data?.data ?? historyRes.data ?? [];
  } catch (error) {
    failDetailRead(revision, error);
  } finally {
    if (isCurrentDetail(revision)) loading.value = false;
  }
}

/**
 * 流程实例模式打开（我发起的 / 抄送 等，直接传 instanceId）
 * 内部自动获取实例信息、流程图、审批记录。
 * 撤销按钮：传入 cancelApi prop，实例状态 RUNNING 时显示。
 */
async function openInstance(instanceId: string, title?: string) {
  reset();
  const revision = detailRevision;
  currentRecordId.value = instanceId;
  internalMode.value = 'view';
  titleText.value = title ?? '流程详情';
  visible.value = true;
  loading.value = true;
  try {
    const [inst, diagramRes, historyRes] = await Promise.all([
      readInstance(instanceId),
      getDiagramData(instanceId),
      getHistory(instanceId),
    ]);
    if (!isCurrentDetail(revision)) return;
    applyInstanceState(inst);
    innerFormData.value = inst.formData || {};
    businessKey.value = inst.businessKey;
    businessType.value = inst.businessType;
    diagramData.value = diagramRes.data?.data ?? diagramRes.data;
    logs.value = historyRes.data?.data ?? historyRes.data ?? [];
  } catch (error) {
    failDetailRead(revision, error);
  } finally {
    if (isCurrentDetail(revision)) loading.value = false;
  }
}

type FlowCreateOptions = {
  title?: string;
  businessType: string;
  formData?: Record<string, any>;
};

async function openCreate(options: FlowCreateOptions) {
  reset();
  internalMode.value = 'view';
  titleText.value = options.title ?? '新建流程单';
  flowStatus.value = 'DRAFT';
  businessType.value = options.businessType;
  innerFormData.value = { ...(options.formData || {}) };
  visible.value = true;
}

defineExpose({ open, openBusinessReadOnly, openTask, openInstance, openCreate });

// ─── 辅助方法 ─────────────────────────────────────────────────

function reset() {
  detailRevision++;
  taskDraftCommentRestored = false;
  businessReadOnly.value = false;
  businessReadAuthContext = '';
  userSearchRevision++;
  loading.value = false;
  loadError.value = '';
  businessFormRef.value = undefined;
  userSearching.value = false;
  userOptions.value = [];
  flowStatus.value = undefined;
  innerFormData.value = {};
  businessType.value = undefined;
  businessKey.value = undefined;
  diagramData.value = null;
  logs.value = [];
  currentTask.value = null;
  currentInstance.value = null;
  currentRecordId.value = '';
  activeTab.value = 'flow';
  actionForm.value = { comment: '', pendingAction: 'APPROVE', transferTo: undefined };
  submitting.value = false;
  actionLoading.value = null;
  detailVisible.value = false;
  transferVisible.value = false;
  nodeOperationVisible.value = false;
  nodeOperationLoading.value = false;
  nodeOperationSubmitting.value = false;
  nodeOperationMode.value = 'jump';
  nodeOperationOptions.value = [];
  nodeOperationForm.value = { targetNodeKey: null, comment: '' };
  forceCompleteVisible.value = false;
  forceCompleteSubmitting.value = false;
  forceCompleteReason.value = '';
  forceCompleteVersion.value = null;
}

watch(
  visible,
  (shown) => {
    if (!shown) reset();
  },
  { flush: 'sync' },
);
onBeforeUnmount(reset);
onDeactivated(() => {
  if (businessReadOnly.value) visible.value = false;
});

function currentBusinessReadAuthContext() {
  const user = authStore.userInfo;
  return JSON.stringify([user?.id, user?.groupId, user?.enabled,
    [...(user?.roles || [])].sort(), [...(user?.roleCodes || [])].sort(),
    [...(authStore.permissions || [])].sort()]);
}
watch(currentBusinessReadAuthContext, () => {
  if (!businessReadOnly.value) return;
  reset();
  titleText.value = '资料读取失败';
  loadError.value = '当前账号或授权已变化，请重新打开业务审批资料';
}, { flush: 'sync' });

function isCurrentDetail(revision: number) {
  return revision === detailRevision && visible.value &&
    (!businessReadOnly.value || businessReadAuthContext === currentBusinessReadAuthContext());
}

function failDetailRead(revision: number, error: any) {
  if (!isCurrentDetail(revision)) return;
  reset();
  titleText.value = '资料读取失败';
  loadError.value = error?.response?.data?.message || error?.message || '无法读取流程资料，请核对当前权限和原记录';
  message.error(loadError.value);
}

async function readInstance(instanceId: string): Promise<FlowInstance> {
  const response = await getInstance(instanceId);
  const instance = response?.data?.data ?? response?.data;
  if (!instance || instance.id !== instanceId) throw new Error('流程实例缺失或与当前记录不一致，请重新核对');
  return instance;
}

function resolveTaskInstanceId(task: FlowTask) {
  return task.instanceId || (task as any).flowInstanceId || '';
}

function openDefinitionDesigner() {
  const id = currentDefinitionId.value;
  if (businessReadOnly.value || !id) return;
  const definitionTitle = currentDefinitionTitle.value;
  detailVisible.value = false;
  visible.value = false;
  router.push({ path: '/flow/definition/design', query: { id, title: definitionTitle } });
}

function applyInstanceState(instance?: FlowInstance | null) {
  currentInstance.value = instance || null;
  flowStatus.value = INSTANCE_STATUS_MAP[instance?.status || ''] ?? instance?.status;
}

async function refreshInstanceFlowData(instanceId: string) {
  const revision = detailRevision;
  if (!isCurrentDetail(revision)) return false;
  loading.value = true;
  try {
    const [inst, diagramRes, historyRes] = await Promise.all([
      readInstance(instanceId),
      getDiagramData(instanceId),
      getHistory(instanceId),
    ]);
    if (!isCurrentDetail(revision)) return false;
    applyInstanceState(inst);
    innerFormData.value = inst.formData || {};
    businessKey.value = inst.businessKey;
    businessType.value = inst.businessType;
    diagramData.value = diagramRes.data?.data ?? diagramRes.data;
    logs.value = historyRes.data?.data ?? historyRes.data ?? [];
    return true;
  } catch (error) {
    failDetailRead(revision, error);
    return false;
  } finally {
    if (isCurrentDetail(revision)) loading.value = false;
  }
}

// ─── view 模式操作 ────────────────────────────────────────────

async function handleSave() {
  if (!canSaveDraft.value || actionLoading.value) return;
  const revision = detailRevision;
  const id = currentRecordId.value;
  const formData = await validateBusinessForm();
  if (!formData || !isCurrentDetail(revision) || actionLoading.value) return;
  actionLoading.value = 'save';
  try {
    if (id) await props.saveApi!(id, formData);
    else await createDraftRecord(formData, revision);
    if (!isCurrentDetail(revision)) return;
    message.success('草稿已保存');
    emit('success');
  } catch (e: any) {
    if (isCurrentDetail(revision)) message.error(e?.message || '保存失败');
  } finally {
    if (isCurrentDetail(revision)) actionLoading.value = null;
  }
}

async function handleSubmit() {
  if (!canSubmitDraft.value || actionLoading.value) return;
  const revision = detailRevision;
  const formData = await validateBusinessForm({ submit: true });
  if (!formData || !isCurrentDetail(revision)) return;
  dialog.info({
    title: '提交审批',
    content: '确认提交审批？',
    positiveText: '确认提交',
    negativeText: '取消',
    onPositiveClick: async () => {
      if (!isCurrentDetail(revision) || actionLoading.value) return;
      actionLoading.value = 'submit';
      try {
        const id = await persistDraftBeforeSubmit(formData, revision);
        if (!id || !isCurrentDetail(revision)) return;
        await props.submitApi!(id);
        if (!isCurrentDetail(revision)) return;
        message.success('提交成功，等待审批');
        visible.value = false;
        emit('success');
      } finally {
        if (isCurrentDetail(revision)) actionLoading.value = null;
      }
    },
  });
}

async function validateBusinessForm(options?: { submit?: boolean }) {
  let formData = innerFormData.value;
  if (businessFormRef.value?.validate) {
    try {
      formData = await businessFormRef.value.validate(options);
    } catch {
      return null;
    }
  }
  return formData;
}

async function persistDraftBeforeSubmit(formData: Record<string, any>, revision: number) {
  if (!isCurrentDetail(revision)) return '';
  const id = currentRecordId.value;
  if (!id) return createDraftRecord(formData, revision);
  if (props.saveApi) await props.saveApi(id, formData);
  return isCurrentDetail(revision) ? id : '';
}

async function createDraftRecord(formData: Record<string, any>, revision: number) {
  if (!isCurrentDetail(revision)) return '';
  if (!props.createApi) {
    throw new Error('当前表单未配置新建草稿接口');
  }
  const resp = await props.createApi(formData);
  if (!isCurrentDetail(revision)) return '';
  const data = normalizeResponseData(resp);
  if (data && typeof data === 'object' && !Array.isArray(data)) {
    innerFormData.value = { ...innerFormData.value, ...data };
  }
  const id = resolveCreatedId(data);
  if (!id) {
    throw new Error('草稿已创建，但接口未返回记录ID，无法继续提交审批');
  }
  currentRecordId.value = id;
  businessKey.value = id;
  return id;
}

function normalizeResponseData(resp: any) {
  return resp?.data?.data ?? resp?.data ?? resp;
}

function resolveCreatedId(data: any) {
  if (typeof data === 'string') return data;
  if (!data || typeof data !== 'object') return '';
  return data.id || data.businessKey || '';
}

async function handleCancel() {
  if (businessReadOnly.value || !props.cancelApi || !visible.value || loading.value) return;
  const revision = detailRevision;
  const id = currentRecordId.value;
  dialog.warning({
    title: '撤销审批',
    content: '确认撤销审批流程？',
    positiveText: '确认撤销',
    negativeText: '取消',
    onPositiveClick: async () => {
      if (!isCurrentDetail(revision) || actionLoading.value) return;
      actionLoading.value = 'cancel';
      try {
        await props.cancelApi!(id);
        if (!isCurrentDetail(revision)) return;
        message.success('撤销成功');
        visible.value = false;
        emit('success');
      } finally {
        if (isCurrentDetail(revision)) actionLoading.value = null;
      }
    },
  });
}

async function handleVoid() {
  if (businessReadOnly.value || !props.voidApi || !visible.value || loading.value) return;
  const revision = detailRevision;
  const id = currentRecordId.value;
  dialog.error({
    title: '作废确认',
    content: '确认作废？作废后不可再发起审批流程。',
    positiveText: '确认作废',
    negativeText: '取消',
    onPositiveClick: async () => {
      if (!isCurrentDetail(revision) || actionLoading.value) return;
      actionLoading.value = 'void';
      try {
        await props.voidApi!(id);
        if (!isCurrentDetail(revision)) return;
        message.success('已作废');
        visible.value = false;
        emit('success');
      } finally {
        if (isCurrentDetail(revision)) actionLoading.value = null;
      }
    },
  });
}

async function handleDelete() {
  if (businessReadOnly.value || !props.deleteApi || !visible.value || loading.value) return;
  const revision = detailRevision;
  const id = currentRecordId.value;
  dialog.warning({
    title: '删除确认',
    content: '确认删除当前记录？审批中的记录需先作废/终止流程后再删除。',
    positiveText: '确认删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      if (!isCurrentDetail(revision) || actionLoading.value) return;
      actionLoading.value = 'delete';
      try {
        await props.deleteApi!(id);
        if (!isCurrentDetail(revision)) return;
        message.success('已删除');
        visible.value = false;
        emit('success');
      } finally {
        if (isCurrentDetail(revision)) actionLoading.value = null;
      }
    },
  });
}

function toNodeOptions(nodes?: FlowOperationNode[] | null) {
  return (nodes || []).map((node) => ({
    label: `${node.name || node.nodeKey}（${node.nodeKey}）`,
    value: node.nodeKey,
  }));
}

async function openNodeOperation(mode: NodeOperationMode) {
  const revision = detailRevision;
  const instance = currentInstance.value;
  if (!instance?.id) return;
  if (mode === 'jump' && !canJumpInstance.value) return;
  if (mode === 'reactivate' && !canReactivateInstance.value) return;

  nodeOperationMode.value = mode;
  nodeOperationForm.value = { targetNodeKey: null, comment: '' };
  nodeOperationOptions.value = toNodeOptions(instance.operationNodes);
  nodeOperationVisible.value = true;

  if (nodeOperationOptions.value.length) return;

  nodeOperationLoading.value = true;
  try {
    const res = await getOperationNodes(instance.id);
    if (!isCurrentDetail(revision) || nodeOperationMode.value !== mode) return;
    const nodes = res.data?.data ?? (res.data as any) ?? [];
    currentInstance.value = {
      ...instance,
      operationNodes: nodes,
    };
    nodeOperationOptions.value = toNodeOptions(nodes);
  } catch (e: any) {
    if (!isCurrentDetail(revision) || nodeOperationMode.value !== mode) return;
    nodeOperationVisible.value = false;
    message.error(e?.response?.data?.message || e?.message || '加载流程节点失败');
  } finally {
    if (isCurrentDetail(revision) && nodeOperationMode.value === mode) nodeOperationLoading.value = false;
  }
}

async function submitNodeOperation() {
  const revision = detailRevision;
  const mode = nodeOperationMode.value;
  const comment = nodeOperationForm.value.comment;
  const instance = currentInstance.value;
  const targetNodeKey = nodeOperationForm.value.targetNodeKey;
  if (!instance?.id || !targetNodeKey) {
    message.warning('请选择目标节点');
    return;
  }

  dialog.warning({
    title: nodeOperationTitle.value,
    content:
      nodeOperationMode.value === 'jump'
        ? `确认将「${instance.title || '该流程'}」跳转到所选节点？`
        : `确认将「${instance.title || '该流程'}」重新激活到所选节点？`,
    positiveText: nodeOperationConfirmText.value,
    negativeText: '取消',
    onPositiveClick: async () => {
      if (!isCurrentDetail(revision) || nodeOperationSubmitting.value) return;
      nodeOperationSubmitting.value = true;
      try {
        const payload = {
          targetNodeKey,
          comment: comment || undefined,
        };
        if (mode === 'jump') {
          await jumpInstance(instance.id!, payload);
          if (!isCurrentDetail(revision)) return;
          message.success('跳转成功');
        } else {
          await reactivateInstance(instance.id!, payload);
          if (!isCurrentDetail(revision)) return;
          message.success('重新激活成功');
        }
        nodeOperationVisible.value = false;
        if (await refreshInstanceFlowData(instance.id!)) emit('success');
      } finally {
        if (isCurrentDetail(revision)) nodeOperationSubmitting.value = false;
      }
    },
  });
}

async function openForceComplete() {
  const revision = detailRevision;
  const instanceId = currentInstance.value?.id;
  if (!canForceCompleteInstance.value || !instanceId) return;
  try {
    const response = await previewBatchForceComplete([instanceId]);
    if (!isCurrentDetail(revision) || currentInstance.value?.id !== instanceId) return;
    const candidates = response.data?.data ?? response.data ?? [];
    const candidate = candidates[0];
    if (!candidate?.eligible || candidate.mutationVersion == null) {
      message.warning(candidate?.reason || '流程已变化，请刷新后重试');
      return;
    }
    forceCompleteVersion.value = candidate.mutationVersion;
    forceCompleteReason.value = '';
    forceCompleteVisible.value = true;
  } catch (error: any) {
    if (isCurrentDetail(revision)) message.error(error?.response?.data?.message || error?.message || '预检失败');
  }
}

async function submitForceComplete() {
  const revision = detailRevision;
  const instanceId = currentInstance.value?.id;
  const version = forceCompleteVersion.value;
  if (!canForceCompleteInstance.value || !instanceId || version == null || forceCompleteSubmitting.value) return;
  const reason = forceCompleteReason.value.trim();
  if (!reason) {
    message.warning('请填写强制通过原因');
    return;
  }
  forceCompleteSubmitting.value = true;
  try {
    const response = await batchForceComplete([{ instanceId, mutationVersion: version }], reason);
    if (!isCurrentDetail(revision) || currentInstance.value?.id !== instanceId) return;
    const result = response.data?.data ?? response.data;
    const item = result?.items?.[0];
    if (item?.status !== 'APPROVED') {
      message.warning(item?.reason || '流程未能结束，请刷新后核对');
      return;
    }
    forceCompleteVisible.value = false;
    message.success('流程已强制通过并结束');
    if (await refreshInstanceFlowData(instanceId)) emit('success');
  } catch (error: any) {
    if (isCurrentDetail(revision)) message.error(error?.response?.data?.message || error?.message || '强制结束失败');
  } finally {
    if (isCurrentDetail(revision)) forceCompleteSubmitting.value = false;
  }
}

function confirmCancelRunningInstance() {
  const revision = detailRevision;
  const instanceId = currentInstance.value?.id;
  if (!canCancelRunningInstance.value || !instanceId) return;
  dialog.warning({
    title: '取消流程',
    content: `确认取消「${currentInstance.value?.title || '该流程'}」？未办任务及运行中的子流程将撤销，不会产生审批通过的业务结果。`,
    positiveText: '确认取消流程',
    negativeText: '返回',
    onPositiveClick: async () => {
      if (!isCurrentDetail(revision) || currentInstance.value?.id !== instanceId) return;
      await cancelInstance(instanceId);
      if (!isCurrentDetail(revision)) return;
      message.success('流程已取消');
      if (await refreshInstanceFlowData(instanceId)) emit('success');
    },
  });
}

function onTabChange() {
  // tab 切换时滚动到顶部（通过重置 scroll，naive scrollbar 无直接 API，靠自然行为）
}

/** 点击折叠状态的垂直 tab：展开面板并切换到对应 tab */
function expandPanel(tabName: typeof activeTab.value) {
  activeTab.value = tabName;
  rightPanelCollapsed.value = false;
}

function hasAction(action: TaskAction) {
  return taskActions.value.includes(action);
}

function actionLabel(action: TaskAction, fallback?: string) {
  return taskActionLabels.value[action] || fallback || action;
}

function openTransferDialog() {
  actionForm.value.transferTo = undefined;
  transferVisible.value = true;
  searchUsers('');
}

async function searchUsers(keyword: string) {
  const revision = detailRevision;
  const searchRevision = ++userSearchRevision;
  userSearching.value = true;
  try {
    const options = await assigneeProvider.searchUsers(keyword);
    if (isCurrentDetail(revision) && searchRevision === userSearchRevision) userOptions.value = options;
  } finally {
    if (isCurrentDetail(revision) && searchRevision === userSearchRevision) userSearching.value = false;
  }
}

async function handleSaveTaskDraft() {
  if (!canSaveTaskDraft.value || submitting.value) return;
  const revision = detailRevision;
  const taskId = currentTask.value!.id;
  actionForm.value.pendingAction = 'SAVE';
  submitting.value = true;
  try {
    const result = await businessFormRef.value.saveTaskDraft({ comment: actionForm.value.comment });
    if (!isCurrentDetail(revision) || currentTask.value?.id !== taskId) return;
    message.success(result?.successMessage || '暂存成功');
    emit('success');
    if (result?.close === true) visible.value = false;
  } catch (e: any) {
    if (isCurrentDetail(revision)) message.error(e?.response?.data?.message || e?.message || '暂存失败');
  } finally {
    if (isCurrentDetail(revision)) submitting.value = false;
  }
}

async function doAction(action: TaskAction) {
  if (!currentTask.value?.id || loading.value || submitting.value || !hasAction(action)) return;
  const revision = detailRevision;
  const taskId = currentTask.value.id;

  if (requiredCommentActions.value.includes(action) && !actionForm.value.comment?.trim()) {
    message.warning('请填写驳回原因');
    return;
  }
  if (action === 'TRANSFER' && !actionForm.value.transferTo) {
    message.warning('请选择转交对象');
    return;
  }

  let formData = innerFormData.value;
  if (businessFormRef.value?.validate) {
    try {
      formData = await businessFormRef.value.validate();
    } catch {
      return;
    }
  }

  if (!isCurrentDetail(revision) || currentTask.value?.id !== taskId || !hasAction(action)) return;
  actionForm.value.pendingAction = action;
  submitting.value = true;
  try {
    const transferToName = userOptions.value.find((u) => u.value === actionForm.value.transferTo)?.label;
    const comment = resolveTaskComment(action, actionForm.value.comment);
    let closeAfterSuccess = true;
    let successMessage = '操作成功';
    if (businessFormRef.value?.runTaskAction) {
      const result = await businessFormRef.value.runTaskAction({
        action,
        task: currentTask.value,
        comment,
        formData,
        transferTo: actionForm.value.transferTo,
        transferToName,
      });
      closeAfterSuccess = result?.close ?? true;
      successMessage = result?.successMessage || successMessage;
    } else {
      await completeTask({
        taskId: currentTask.value.id!,
        action,
        comment,
        formData,
        transferTo: actionForm.value.transferTo,
        transferToName,
      });
    }
    if (!isCurrentDetail(revision)) return;
    message.success(successMessage);
    transferVisible.value = false;
    emit('success');
    if (closeAfterSuccess) {
      visible.value = false;
    }
  } catch (e: any) {
    if (isCurrentDetail(revision)) message.error(e?.response?.data?.message || e?.message || '操作失败');
  } finally {
    if (isCurrentDetail(revision)) submitting.value = false;
  }
}
</script>

<style scoped>
.ffd-drawer-content :deep(.n-drawer-body) {
  flex: 1 1 auto;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ffd-drawer-content :deep(.n-drawer-body > .n-scrollbar-container) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ffd-drawer-content :deep(.n-drawer-body > .n-scrollbar-container > .n-scrollbar-content) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ffd-drawer-content :deep(.n-drawer-body-content-wrapper) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ffd-main {
  display: flex;
  flex: 1;
  overflow: hidden;
  width: 100%;
  min-height: 0;
  height: 100%;
}

.ffd-detail-drawer,
.ffd-detail-drawer :deep(.n-drawer-content-wrapper),
.ffd-detail-drawer :deep(.n-drawer-content),
.ffd-detail-drawer :deep(.n-drawer-body),
.ffd-detail-drawer :deep(.n-drawer-body-content-wrapper),
.ffd-detail-drawer :deep(.page-form) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ffd-detail-drawer :deep(.n-drawer-body) {
  flex: 1 1 auto;
}

.ffd-detail-scroll {
  flex: 1;
  height: 100%;
  min-height: 0;
}

.ffd-detail-body {
  height: 100%;
  min-height: 0;
}

.ffd-detail-tabs {
  height: 100%;
  min-height: 0;
}

.ffd-detail-tabs :deep(.n-tabs-pane-wrapper),
.ffd-detail-tabs :deep(.n-tab-pane) {
  height: 100%;
  min-height: 0;
}

.ffd-detail-diagram {
  height: min(560px, calc(100vh - 220px));
  min-height: 360px;
}

.ffd-detail-empty {
  padding-top: 120px;
}

.ffd-left {
  flex: 1;
  overflow: hidden;
  min-width: 0;
  min-height: 0;
  height: 100%;
}

.ffd-left--fixed-layout {
  display: flex;
}

.ffd-left-scroll {
  height: 100%;
  min-height: 0;
}

.ffd-left-scroll-body {
  min-height: 100%;
  padding: 20px 24px;
}

.ffd-left-fixed-shell {
  flex: 1;
  min-height: 0;
  height: 100%;
  padding: 20px 24px;
  overflow: hidden;
}

.ffd-left-fixed-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.ffd-left-fixed-body :deep(.n-spin),
.ffd-left-fixed-body :deep(.n-spin-container),
.ffd-left-fixed-body :deep(.n-spin-content),
.ffd-left-fixed-body :deep(.n-spin-body) {
  height: 100%;
  min-height: 0;
}

/* ── 右侧面板容器 ─────────────────────────────────────── */
.ffd-right {
  width: 360px;
  flex-shrink: 0;
  min-height: 0;
  height: 100%;
  border-left: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fafafa;
  transition: width 0.2s ease;
}
.ffd-right--collapsed {
  width: 40px;
}

/* ── 展开状态：Tab 栏（tabs + 折叠按钮并排） ─────────── */
.ffd-tabs-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}
.ffd-tabs {
  flex: 1;
  min-width: 0;
  padding: 0 8px;
}
.ffd-collapse-btn {
  flex-shrink: 0;
  padding: 0 8px;
  height: 100%;
  color: #999;
}
.ffd-collapse-btn:hover {
  color: #333;
}

.ffd-flow-toolbar {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  padding: 10px 14px 0;
  background: #fafafa;
}

/* Tab 内容区可滚动 */
.ffd-tab-body {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

/* ── 折叠状态：垂直 tab 条 ───────────────────────────── */
.ffd-vtabs {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 12px;
  gap: 4px;
  width: 100%;
}
.ffd-vtab {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 14px 0;
  cursor: pointer;
  border-radius: 4px;
  color: #888;
  font-size: 12px;
  transition:
    background 0.15s,
    color 0.15s;
}
.ffd-vtab:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #333;
}
.ffd-vtab--active {
  color: v-bind("'var(--primary-color, #18a058)'");
  background: rgba(24, 160, 88, 0.08);
}
.ffd-vtab span {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  letter-spacing: 3px;
  white-space: nowrap;
}
</style>
