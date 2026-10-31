<template>
  <NCard title="联动任务" size="small" class="detail-section">
    <template #header-extra
      ><NSpace>
        <NButton
          v-if="canManage && !closed && risk.workflow?.canCreateLinkage"
          type="primary"
          size="small"
          :disabled="loading || saving"
          @click="openCreate"
          >发起事件联动</NButton
        >
        <NButton text :loading="loading" @click="load()">刷新进度</NButton>
      </NSpace></template
    >
    <NAlert v-if="error" type="error" :bordered="false">{{ error }}</NAlert>
    <NEmpty
      v-if="!loading && !tasks.length && !error"
      :description="
        risk.workflow?.canCreateLinkage
          ? '暂无联动任务，可按本事件的处置需要发起。'
          : '暂无联动任务；确认风险并指派责任人后可发起。'
      "
    />
    <NCard v-for="task in tasks" :key="task.id" size="small" class="task-card">
      <template #header><HDictText type="museum_safety_linkage_type" :value="task.type" /></template>
      <template #header-extra><HDictText type="museum_safety_linkage_status" :value="task.status" /></template>
      <NDescriptions :column="1" label-placement="left" size="small">
        <NDescriptionsItem label="关联事件"
          >{{ task.eventContext?.title || risk.title }}
          <NTag v-if="task.currentEvent === false" size="small" type="warning">历史轮次</NTag></NDescriptionsItem
        >
        <NDescriptionsItem label="事件设备"><DeviceReference
          :device-id="task.eventContext?.deviceId || risk.deviceId"
          :name="task.eventContext ? task.eventContext.deviceName : risk.deviceName" /></NDescriptionsItem>
        <NDescriptionsItem v-if="task.eventContext?.sourceEventId" label="来源采集事件"><AlarmEventReference :event-id="task.eventContext.sourceEventId" /></NDescriptionsItem>
        <NDescriptionsItem label="触发位置"><LocationReference
          :location-id="task.eventContext ? task.eventContext.locationId : risk.locationSnapshotId"
          :name="task.eventContext ? task.eventContext.locationPath : risk.locationSnapshotPath" /></NDescriptionsItem>
        <NDescriptionsItem v-if="task.eventContext?.originLogId" label="发起记录">
          <NButton text type="primary" @click="focusOrigin(task)"
            >{{ originLabel(task) }} · {{ formatDateTime(task.createdAt) }}</NButton
          >
        </NDescriptionsItem>
        <NDescriptionsItem v-else label="关联依据">历史任务关联此风险，未保存发起时的事件快照。</NDescriptionsItem>
        <NDescriptionsItem label="责任人"><UserText :value="task.assigneeId" placeholder="未指派" /></NDescriptionsItem>
        <NDescriptionsItem label="任务说明"
          ><div class="long-text">{{ task.instruction }}</div></NDescriptionsItem
        >
        <NDescriptionsItem v-if="task.result" label="执行结果"
          ><div class="long-text">{{ task.result }}</div></NDescriptionsItem
        >
        <NDescriptionsItem v-if="task.attachments?.length" label="现场附件">
          <NSpace
            ><NButton
              v-for="(file, index) in task.attachments"
              :key="file"
              v-download="{ url: file, fileName: `现场附件 ${index + 1}`, preferServerFilename: true }"
              text
              type="primary"
              >下载附件 {{ index + 1 }}</NButton
            ></NSpace
          >
        </NDescriptionsItem>
        <NDescriptionsItem v-if="task.externalId" label="关联记录">
          <NButton v-if="task.type === 'INSPECTION'" text type="primary" @click="openInspection(task)"
            >查看巡检工单</NButton
          >
          <span v-else>站内通知已生成，{{ task.status === 'COMPLETED' ? '责任人已阅读' : '等待责任人阅读' }}</span>
        </NDescriptionsItem>
      </NDescriptions>
      <NAlert
        v-if="
          canManage &&
          !closed &&
          ['PENDING', 'FAILED'].includes(task.status) &&
          !task.externalId &&
          needsConfiguration(task)
        "
        type="warning"
        :bordered="false"
        >{{ !task.assigneeId ? '请先指派责任人。' : '请先选择覆盖本事件的巡检计划。' }}</NAlert
      >
      <NAlert v-if="task.lastError" type="error" :bordered="false">{{ task.lastError }}</NAlert>
      <NText v-if="task.type === 'INSPECTION' && task.status === 'PROCESSING'" depth="3"
        >完成工单中的必检点后，任务进入待复核。</NText
      >
      <NText v-if="manual(task) && task.status === 'PROCESSING'" depth="3"
        >请现场执行并记录实际结果及附件，提交后由管理人员复核。</NText
      >
      <NCollapse v-if="task.logs.length" style="margin: 12px 0"
        ><NCollapseItem name="logs" :title="`任务记录（${task.logs.length}）`">
          <NTimeline>
            <NTimelineItem
              v-for="log in task.logs"
              :key="log.id"
              :title="logLabel(log.action)"
              :time="formatDateTime(log.occurredAt)"
              >{{ log.remark }}</NTimelineItem
            >
          </NTimeline>
        </NCollapseItem></NCollapse
      >
      <NSpace v-if="canManage && !terminal(task)">
        <NButton
          v-if="!closed && !task.externalId && ['PENDING', 'FAILED'].includes(task.status)"
          size="small"
          @click="openAction(task, 'ASSIGN')"
          :type="needsConfiguration(task) ? 'primary' : 'default'"
          :disabled="saving || loading"
          >{{ needsConfiguration(task) ? (task.assigneeId ? '配置巡检计划' : '指派责任人') : '调整配置' }}</NButton
        >
        <NButton
          v-if="!closed && task.status === 'PENDING'"
          size="small"
          type="primary"
          :disabled="saving || loading || needsConfiguration(task)"
          @click="execute(task, 'START')"
          >开始执行</NButton
        >
        <NButton
          v-if="!closed && task.status === 'FAILED'"
          size="small"
          type="warning"
          :disabled="saving || loading || (!task.externalId && needsConfiguration(task))"
          @click="execute(task, 'RETRY')"
          >重试</NButton
        >
        <NButton
          v-if="!closed && manual(task) && task.status === 'PROCESSING'"
          size="small"
          type="primary"
          :disabled="saving || loading"
          @click="openAction(task, 'SUBMIT')"
          >填写执行结果</NButton
        >
        <NButton
          v-if="!closed && task.status === 'REVIEWING'"
          size="small"
          type="success"
          :disabled="saving || loading"
          @click="openAction(task, 'VERIFY')"
          >复核完成</NButton
        >
        <NButton
          v-if="!closed && manual(task) && task.status === 'REVIEWING'"
          size="small"
          :disabled="saving || loading"
          @click="openAction(task, 'REJECT')"
          >退回补充</NButton
        >
        <NButton
          v-if="!closed && task.externalId && task.status === 'PROCESSING'"
          size="small"
          :disabled="saving || loading"
          @click="execute(task, 'REFRESH')"
          >同步下游进度</NButton
        >
        <NButton
          v-if="!task.externalId"
          size="small"
          type="error"
          :disabled="saving || loading"
          @click="openAction(task, 'CANCEL')"
          >取消任务</NButton
        >
      </NSpace>
    </NCard>
  </NCard>
  <HPageForm v-model:show="createShow" title="发起事件联动" content-only :footer-style="{ paddingRight: '36px' }">
    <NAlert v-if="createError" type="error">{{ createError }}</NAlert>
    <NDescriptions :column="1" bordered label-placement="left" size="small">
      <NDescriptionsItem label="风险事件">{{ risk.title }}</NDescriptionsItem>
      <NDescriptionsItem label="领域">{{ domainLabels[risk.domain] }}</NDescriptionsItem>
      <NDescriptionsItem label="触发设备"><DeviceReference :device-id="risk.deviceId" :name="risk.deviceName" /></NDescriptionsItem>
      <NDescriptionsItem label="触发位置"><LocationReference :location-id="risk.locationSnapshotId" :name="risk.locationSnapshotPath" /></NDescriptionsItem>
    </NDescriptions>
    <NForm label-placement="left" label-width="100" style="margin-top: 16px">
      <NFormItem label="任务类型" required
        ><NSelect
          v-model:value="createForm.type"
          :options="createTypeOptions"
          :disabled="saving"
          @update:value="suggestInstruction"
      /></NFormItem>
      <NFormItem label="处置要求" required
        ><NInput
          v-model:value="createForm.instruction"
          type="textarea"
          :maxlength="2000"
          :disabled="saving"
          placeholder="说明本事件需要核查的对象、执行措施和完成要求"
      /></NFormItem>
    </NForm>
    <NText depth="3">任务绑定本轮事件、设备和触发位置，并沿用风险责任人。巡检计划及执行结果在任务中登记。</NText>
    <template #form-action
      ><NButton
        type="primary"
        :loading="saving"
        :disabled="!createForm.type || !createForm.instruction.trim()"
        style="margin-right: 12px"
        @click="createTask"
        >创建联动任务</NButton
      ></template
    >
  </HPageForm>
  <HPageForm v-model:show="formShow" :title="actionTitle" content-only :footer-style="{ paddingRight: '36px' }">
    <NAlert v-if="formError" type="error">{{ formError }}</NAlert>
    <NForm label-placement="left" label-width="90">
      <template v-if="formAction === 'ASSIGN'">
        <NFormItem label="责任人" required><UserSelect v-model:value="form.assigneeId" :disabled="saving" /></NFormItem>
        <NAlert type="info" :bordered="false"
          >责任人须在风险所属场馆登记为有效值班人员，巡检还须满足计划资质要求。</NAlert
        >
        <NFormItem v-if="selected?.type === 'INSPECTION'" label="巡检计划" required
          ><NSelect
            v-model:value="form.planId"
            :options="planOptions"
            :disabled="saving"
            filterable
            clearable
            placeholder="选择覆盖风险设备或触发位置的计划"
        /></NFormItem>
      </template>
      <NFormItem v-else label="操作说明" required
        ><NInput v-model:value="form.result" type="textarea" :maxlength="2000" :disabled="saving"
      /></NFormItem>
      <NFormItem v-if="formAction === 'SUBMIT'" label="现场附件"
        ><UploadFiles
          :key="formGeneration"
          :form="form"
          upload-field="attachments"
          module="museum-safety"
          list-type="text"
          label="上传附件"
          :disabled="saving"
      /></NFormItem>
    </NForm>
    <template #form-action
      ><NButton
        type="primary"
        :loading="saving"
        :disabled="app.uploading || !validForm"
        style="margin-right: 12px"
        @click="submit"
        >提交</NButton
      ></template
    >
  </HPageForm>
</template>
<script setup lang="ts">
import type { SafetyRiskVo } from '../typings';
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAppStore } from '@/store';
import { formatDateTime } from '@/utils';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import UserText from '@/modules/sys/components/UserText.vue';
import DeviceReference from '@/modules/museum-device/components/DeviceReference.vue';
import LocationReference from '@/modules/museum-device/components/LocationReference.vue';
import AlarmEventReference from '@/modules/museum-device/components/AlarmEventReference.vue';
import UploadFiles from '@/components/form/field/upload/upload.vue';
import { safetyApi, unwrapResponse } from '../api';
const props = defineProps<{ riskId: string; orgId: string; closed: boolean; canManage: boolean; risk: SafetyRiskVo }>();
const emit = defineEmits<{ pending: [count: number | null]; changed: []; 'show-origin': [logId: string] }>();
interface Task {
  id: string;
  type: string;
  status: string;
  assigneeId: string | null;
  instruction: string;
  result?: string;
  externalId?: string;
  planId?: string;
  lastError?: string;
  attachments: string[];
  revision: number;
  createdAt: number;
  currentEvent: boolean;
  eventContext?: {
    title: string;
    deviceId: string;
    deviceName?: string;
    locationId?: string;
    locationPath?: string;
    sourceEventId?: string;
    triggerLogId?: string;
    originLogId?: string;
  };
  logs: Array<{ id: string; action: string; remark: string; occurredAt: number }>;
}
const router = useRouter(),
  app = useAppStore();
const tasks = ref<Task[]>([]),
  loading = ref(false),
  error = ref('');
const formShow = ref(false),
  saving = ref(false),
  formError = ref(''),
  selected = ref<Task | null>(null),
  formAction = ref(''),
  formGeneration = ref(0);
const createShow = ref(false),
  createError = ref('');
const createForm = reactive({ type: '', instruction: '', expectedTriggerLogId: '' });
const typeLabels: Record<string, string> = {
  NOTIFICATION: '站内通知',
  INSPECTION: '巡检工单',
  POWER_OFF_SUGGESTION: '断电建议',
  FIRE_LINKAGE_TASK: '消防现场处置',
  SECURITY_LINKAGE_TASK: '安防现场处置',
};
const domainLabels: Record<string, string> = {
  BUILDING: '建筑结构',
  LIGHTNING: '防雷',
  FIRE: '消防',
  SECURITY: '安防',
  ELECTRICITY: '用电',
  BUILDING_ENVIRONMENT: '建筑环境',
};
const createTypeOptions = computed(() =>
  (props.risk.workflow?.linkageTypes || []).map((type) => ({ label: typeLabels[type] || type, value: type })),
);
function openCreate() {
  createError.value = '';
  createForm.type =
    createTypeOptions.value.find((t) => t.value === 'INSPECTION')?.value || createTypeOptions.value[0]?.value || '';
  createForm.expectedTriggerLogId = props.risk.workflow?.triggerLogId || '';
  suggestInstruction();
  createShow.value = true;
}
function suggestInstruction() {
  const risk = props.risk,
    target = `${risk.deviceName || risk.deviceId}（${risk.locationSnapshotPath || '触发位置未记录'}）`;
  const requirements: Record<string, string> = {
    NOTIFICATION: '通知责任人核查该事件并确认接收。',
    INSPECTION: '完成相关必检点，记录复测结果；发现异常须登记处置并复核。',
    POWER_OFF_SUGGESTION: '现场评估电气风险及断电必要性，按操作要求执行，提交实际结果并复核。',
    FIRE_LINKAGE_TASK: '现场排查火情和消防设施，执行相应处置，提交结果及附件并复核。',
    SECURITY_LINKAGE_TASK: '核查安防异常及现场情况，执行处置，提交结果及附件并复核。',
  };
  createForm.instruction = `针对“${risk.title}”，核查 ${target}。${requirements[createForm.type] || ''}`;
}
async function createTask() {
  if (saving.value || !createForm.type || !createForm.instruction.trim()) return;
  saving.value = true;
  createError.value = '';
  try {
    await safetyApi.createRiskLinkage(props.riskId, { ...createForm });
    createShow.value = false;
    await load(false);
    emit('changed');
  } catch (cause: any) {
    createError.value = cause?.message || '联动任务创建失败';
  } finally {
    saving.value = false;
  }
}
function originLabel(task: Task) {
  const action = props.risk.logs.find((log) => log.id === task.eventContext?.originLogId)?.action;
  return action === 'TRIGGERED' ? '风险触发时创建' : '处置阶段发起';
}
function focusOrigin(task: Task) {
  if (task.eventContext?.originLogId) emit('show-origin', task.eventContext.originLogId);
}
const needsConfiguration = (task: Task) => !task.assigneeId || (task.type === 'INSPECTION' && !task.planId);
const plans = ref<Array<{ id: string; name: string }>>([]);
const planOptions = computed(() => plans.value.map((p) => ({ label: p.name, value: p.id })));
const form = reactive({
  assigneeId: null as string | null,
  planId: null as string | null,
  result: '',
  attachments: [] as string[],
});
const actionTitle = computed(
  () =>
    ({
      ASSIGN: '指派联动任务',
      SUBMIT: '填写执行结果',
      VERIFY: '复核联动结果',
      REJECT: '退回补充',
      CANCEL: '取消联动任务',
    })[formAction.value] || '联动任务',
);
const validForm = computed(() =>
  formAction.value === 'ASSIGN'
    ? Boolean(form.assigneeId && (selected.value?.type !== 'INSPECTION' || form.planId))
    : Boolean(form.result.trim()),
);
const manual = (task: Task) => !['NOTIFICATION', 'INSPECTION'].includes(task.type);
const terminal = (task: Task) => ['COMPLETED', 'CANCELLED'].includes(task.status);
let generation = 0,
  timer: ReturnType<typeof setTimeout> | undefined,
  disposed = false;
function logLabel(action: string) {
  return (
    (
      {
        ASSIGNED: '任务指派',
        STARTED: '开始执行',
        RETRIED: '恢复同步',
        FAILED: '执行失败',
        SUBMITTED: '提交结果',
        VERIFIED: '复核完成',
        REJECTED: '退回补充',
        CANCELLED: '任务取消',
        RECEIVED: '通知已读',
      } as Record<string, string>
    )[action] || action
  );
}
async function load(notifyChange = true) {
  const token = ++generation;
  loading.value = true;
  try {
    const response = await safetyApi.riskLinkages(props.riskId);
    if (disposed || token !== generation) return;
    const previous = tasks.value;
    tasks.value = unwrapResponse<Task[]>(response) || [];
    const changed =
      previous.length > 0 &&
      JSON.stringify(previous.map((t) => [t.id, t.status, t.revision])) !==
        JSON.stringify(tasks.value.map((t) => [t.id, t.status, t.revision]));
    error.value = '';
    emit('pending', tasks.value.filter((t) => !terminal(t)).length);
    if (notifyChange && changed) emit('changed');
  } catch (cause: any) {
    if (token === generation && !disposed) {
      error.value = cause?.message || '联动任务加载失败';
      emit('pending', null);
    }
  } finally {
    if (token === generation && !disposed) {
      loading.value = false;
      clearTimeout(timer);
      timer = setTimeout(() => {
        if (tasks.value.some((t) => !terminal(t))) void load();
      }, 10000);
    }
  }
}
function openInspection(task: Task) {
  void router.push({
    path: '/museum-safety/operations',
    query: { orgId: props.orgId, tab: 'task', taskId: task.externalId },
  });
}
async function openAction(task: Task, action: string) {
  selected.value = { ...task };
  formAction.value = action;
  formError.value = '';
  formGeneration.value++;
  Object.assign(form, { assigneeId: task.assigneeId, planId: task.planId || null, result: '', attachments: [] });
  formShow.value = true;
  if (action === 'ASSIGN' && task.type === 'INSPECTION') {
    try {
      plans.value = unwrapResponse(await safetyApi.inspectionPlans({ orgId: props.orgId })) || [];
    } catch (cause: any) {
      formError.value = cause?.message || '巡检计划加载失败';
    }
  }
}
async function execute(task: Task, action: string, extra: Record<string, unknown> = {}) {
  if (saving.value) return false;
  saving.value = true;
  error.value = '';
  formError.value = '';
  try {
    await safetyApi.riskLinkageAction(props.riskId, task.id, { action, expectedRevision: task.revision, ...extra });
    await load(false);
    emit('changed');
    return true;
  } catch (cause: any) {
    const message = cause?.message || '联动操作失败，请刷新重试';
    if (formShow.value) formError.value = message;
    else error.value = message;
    return false;
  } finally {
    saving.value = false;
  }
}
async function submit() {
  if (
    selected.value &&
    validForm.value &&
    !app.uploading &&
    (await execute(selected.value, formAction.value, { ...form }))
  )
    formShow.value = false;
}
watch(
  () => props.riskId,
  () => {
    clearTimeout(timer);
    emit('pending', null);
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  disposed = true;
  ++generation;
  clearTimeout(timer);
});
defineExpose({ refresh: () => load(false) });
</script>
<style scoped>
.detail-section {
  margin-top: 16px;
}
.long-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.task-card {
  margin-top: 12px;
}
</style>
