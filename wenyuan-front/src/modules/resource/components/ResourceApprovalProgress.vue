<template>
  <NCard :title="title" size="small" class="resource-approval-progress" aria-live="polite">
    <template #header-extra>
      <NButton v-if="!acquisitionRestricted" size="small" text :loading="loading" @click="refresh">刷新进度</NButton>
    </template>
    <NAlert v-if="acquisitionRestricted" type="info">当前账号无权查看审批进度。</NAlert>
    <NText v-else-if="loading">正在读取审批进度…</NText>
    <NAlert v-else-if="error" type="error">{{ error }} 请点击“刷新进度”重试。</NAlert>
    <NSpace v-else-if="data" vertical :size="12">
      <NSpace align="center">
        <FlowStatusTag :status="data.flowStatus" />
        <NText>{{ statusHint }}</NText>
      </NSpace>
      <NDescriptions v-if="data.flowStatus === 'AUDIT'" :column="1" label-placement="left" size="small">
        <NDescriptionsItem label="当前节点">{{ currentNodes || '待流程更新' }}</NDescriptionsItem>
        <NDescriptionsItem label="待处理人">{{
          pendingPeople || '暂无待处理人信息，请查看流程或刷新'
        }}</NDescriptionsItem>
      </NDescriptions>
      <NText v-if="latestEvent" depth="3" class="approval-event">
        最近处理：{{ latestEvent.operatorName || '系统' }} ·
        {{ latestEvent.actionLabel || latestEvent.action || '流程更新' }}
        <template v-if="latestEvent.createTime">
          · {{ formatLocalDateValue(latestEvent.createTime, 'YYYY-MM-DD HH:mm') }}</template
        >
        <template v-if="latestEvent.comment"> · {{ latestEvent.comment }}</template>
      </NText>
      <NSpace>
        <NButton v-if="data.flowInstanceId || data.history.length" size="small" @click="detailShow = true"
          >查看流程与记录</NButton
        >
        <NButton
          v-if="data.flowStatus === 'AUDIT' && taskTarget"
          size="small"
          type="primary"
          secondary
          @click="openMyTasks"
          >前往我的待办</NButton
        >
      </NSpace>
      <NText v-if="data.flowStatus === 'AUDIT'" depth="3"
        >审批由当前节点处理人办理；“我的待办”仅显示当前账号有权处理的任务。</NText
      >
    </NSpace>
  </NCard>
  <NModal v-model:show="detailShow" preset="card" :title="title" style="width: min(1000px, calc(100vw - 32px))">
    <FlowInfoPanel v-if="detailShow && data" :data="data" />
  </NModal>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { NAlert, NButton, NCard, NDescriptions, NDescriptionsItem, NModal, NSpace, NText } from 'naive-ui';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { formatLocalDateValue } from '@/utils';
import FlowStatusTag from '@/modules/flow/components/viewer/FlowStatusTag.vue';
const FlowInfoPanel = defineAsyncComponent(
  async () => (await import('@/modules/flow/components/viewer/FlowInfoPanel.vue')).default,
);
import type { FlowDiagramData, FlowInstanceLog } from '@/modules/flow/typings/flow';
import { resourceMenuTarget } from '../utils/navigation';

interface ApprovalData {
  businessKey: string;
  businessType: string;
  flowStatus: string;
  flowInstanceId?: string | null;
  diagram?: FlowDiagramData | null;
  history: FlowInstanceLog[];
}
const props = withDefaults(
  defineProps<{
    businessId: string;
    businessType: string;
    fetchFlow: (id: string) => Promise<unknown>;
    title?: string;
    revision?: unknown;
  }>(),
  { title: '审批进度' },
);
const auth = useAuthStore();
const router = useRouter();
const acquisitionRestricted = computed(() => false);
const businessTypeNames: Record<string, string> = {
  RESOURCE_IMPORT: '数字资源入库',
  RESOURCE_BUSINESS_REVIEW: '数字资源业务审核',
};
const data = ref<ApprovalData>();
const loading = ref(false);
const error = ref('');
const detailShow = ref(false);
let generation = 0;
let active = true;

function invalidate() {
  generation++;
  data.value = undefined;
  error.value = '';
  loading.value = false;
  detailShow.value = false;
}
async function refresh() {
  invalidate();
  if (!active || !props.businessId || acquisitionRestricted.value || !auth.userInfo?.id || auth.identitySyncing) return;
  const owned = generation;
  const id = props.businessId;
  const businessType = props.businessType;
  loading.value = true;
  try {
    const response = (await props.fetchFlow(id)) as { data?: ApprovalData };
    if (!active || owned !== generation) return;
    const value = response?.data;
    if (
      !value ||
      value.businessKey !== id ||
      value.businessType !== businessType ||
      !value.flowStatus ||
      !Array.isArray(value.history)
    )
      throw new Error('审批进度与当前业务不一致');
    data.value = value;
  } catch {
    if (active && owned === generation) error.value = '审批进度读取失败或当前账号无权查看。';
  } finally {
    if (owned === generation) loading.value = false;
  }
}
watch(
  () => [
    props.businessId,
    props.businessType,
    props.fetchFlow,
    props.revision,
    auth.userInfo?.id,
    auth.identityRevision,
    auth.identitySyncing,
    JSON.stringify(auth.roles),
    JSON.stringify(auth.roleCodes),
    JSON.stringify(auth.permissions),
  ],
  refresh,
  { immediate: true, flush: 'sync' },
);
onDeactivated(() => {
  active = false;
  invalidate();
});
onActivated(() => {
  if (!active) {
    active = true;
    void refresh();
  }
});
onBeforeUnmount(() => {
  active = false;
  invalidate();
});

const currentNodes = computed(() => {
  const diagram = data.value?.diagram;
  return (
    diagram?.currentNodeKeys
      .map(
        (key) =>
          diagram.model.nodes.find((node) => node.properties.nodeKey === key || node.id === key)?.properties.name ||
          key,
      )
      .join('、') || ''
  );
});
const pendingPeople = computed(() => {
  const tasks = data.value?.diagram?.pendingTasks || [];
  return [
    ...new Set(
      tasks.map(
        (task) =>
          `${task.nodeName || '当前节点'}：${task.assigneeName || (task.assignee ? `账号 ${task.assignee}` : '待分配')}`,
      ),
    ),
  ].join('；');
});
const latestEvent = computed(() =>
  data.value?.history.filter((event) => event.instanceId === data.value?.flowInstanceId).at(-1),
);
const statusHint = computed(() => {
  const value = data.value;
  if (!value) return '';
  if (!value.flowInstanceId) return value.flowStatus === 'PASS' ? '已确认，无线上审批记录' : '尚未发起线上审批';
  const hints: Record<string, string> = {
    AUDIT: '已提交，等待当前节点处理',
    PASS: '审批已通过',
    REJECT: '审批已驳回，请查看处理意见',
    CANCEL: '审批已撤销',
    VOID: '本次业务已作废',
    DRAFT: '当前为草稿，请核实后提交',
  };
  return hints[value.flowStatus] || '请查看流程记录';
});
const taskTarget = computed(() => resourceMenuTarget(auth.menus || [], '/flow/task'));
function openMyTasks() {
  const target = taskTarget.value;
  if (!target || data.value?.flowStatus !== 'AUDIT' || !data.value.flowInstanceId) return;
  void router.push({
    ...target,
    query: {
      ...target.query,
      tab: 'todo',
      businessType: props.businessType,
      businessTypeName: businessTypeNames[props.businessType] || props.businessType,
      instanceId: data.value.flowInstanceId,
    },
  });
}
defineExpose({ refresh });
</script>

<style scoped>
.approval-event {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
