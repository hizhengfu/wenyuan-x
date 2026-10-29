<template>
  <div class="risk-event-handling">
    <HPageForm
      v-model:show="visible"
      title="风险详情"
      content-only
      cancel-text="关闭"
      :footer-style="{ paddingRight: '36px' }"
    >
      <NAlert v-if="!canSelect" type="warning">无权查看风险事件，请联系管理员授予场馆安全查看权限。</NAlert>
      <NAlert v-else-if="detailError" type="error" :bordered="false">
        {{ detailError }}
        <NButton text type="primary" @click="loadDetail()">重试</NButton>
      </NAlert>
      <NSpin v-if="canSelect" :show="detailLoading">
        <template v-if="detail">
          <NDescriptions label-placement="left" bordered :column="2">
            <NDescriptionsItem label="风险">{{ detail.title }}</NDescriptionsItem>
            <NDescriptionsItem label="状态"
              ><HDictText type="museum_safety_risk_status" :value="detail.status"
            /></NDescriptionsItem>
            <NDescriptionsItem label="场馆">{{ venueName(detail.orgId) }}</NDescriptionsItem>
            <NDescriptionsItem label="领域">{{ domainLabel(detail.domain) }}</NDescriptionsItem>
            <NDescriptionsItem label="等级"
              ><HDictText type="museum_safety_risk_level" :value="detail.level"
            /></NDescriptionsItem>
            <NDescriptionsItem label="设备"><DeviceReference :device-id="detail.deviceId" :name="detail.deviceName" /></NDescriptionsItem>
            <NDescriptionsItem label="责任人"
              ><UserText :value="detail.assigneeId" placeholder="未指派"
            /></NDescriptionsItem>
            <NDescriptionsItem label="触发位置"><LocationReference :location-id="detail.locationSnapshotId" :name="detail.locationSnapshotPath" /></NDescriptionsItem>
            <NDescriptionsItem label="当前位置" :span="2"><LocationReference :location-id="detail.currentLocationId" :name="detail.currentLocationPath" /></NDescriptionsItem>
            <NDescriptionsItem label="说明" :span="2">
              <div class="detail-description">{{ detail.description || '--' }}</div>
            </NDescriptionsItem>
          </NDescriptions>
          <NAlert v-if="locationMoved" type="warning" class="detail-section"
            >设备当前位置与风险触发位置不同，请结合位置变更记录判断。</NAlert
          >

          <NCard size="small" title="处置进度" class="detail-section">
            <template #header-extra
              ><NButton
                text
                :loading="detailLoading"
                :disabled="submitting || appStore.uploading"
                @click="refreshDetail"
                >刷新进度</NButton
              ></template
            >
            <NSteps
              v-if="detail.workflow"
              :current="Math.min(detail.workflow.step + 1, 6)"
              :status="detail.status === 'CLOSED' ? 'finish' : 'process'"
              size="small"
            >
              <NStep title="确认风险" />
              <NStep title="指派责任人" />
              <NStep title="处置及联动" />
              <NStep title="来源恢复" />
              <NStep title="复核恢复" />
              <NStep title="关闭风险" />
            </NSteps>
            <NAlert v-else type="warning" :bordered="false">处置进度未加载，请刷新详情。</NAlert>
          </NCard>
          <NCard
            v-if="canManage && detail.status !== 'CLOSED'"
            size="small"
            title="当前处置步骤"
            class="detail-section"
          >
            <NAlert v-if="actionError" type="error" :bordered="false">{{ actionError }}</NAlert>
            <NAlert v-if="primaryBlocked" type="warning" :bordered="false">{{ primaryBlocked }}</NAlert>
            <NText v-if="primaryAction">{{ actionLabel(primaryAction) }}</NText>
            <NForm v-if="primaryAction" label-placement="left" label-width="100" style="margin-top: 16px">
              <NFormItem v-if="primaryAction === 'ASSIGNED'" label="责任人" required>
                <UserSelect v-model:value="actionForm.assigneeId" clearable placeholder="请选择责任人" />
              </NFormItem>
              <NFormItem
                v-if="['PROCESSED', 'RECOVERED', 'RECOVERY_CONFIRMED', 'CLOSED'].includes(primaryAction)"
                :label="primaryAction === 'PROCESSED' ? '处理结果' : '复核说明'"
                :required="primaryAction === 'PROCESSED'"
              >
                <NInput v-model:value="actionForm.result" type="textarea" :maxlength="2000" />
              </NFormItem>
              <NFormItem label="备注"
                ><NInput v-model:value="actionForm.remark" type="textarea" :maxlength="2000"
              /></NFormItem>
              <NFormItem label="附件">
                <UploadFiles
                  :key="attachmentGeneration"
                  :form="actionForm"
                  upload-field="attachments"
                  module="museum-safety"
                  list-type="text"
                  label="上传附件"
                  :disabled="submitting"
                />
              </NFormItem>
            </NForm>
            <NSpace style="margin-top: 12px">
              <NButton
                v-if="detail.workflow && detail.workflow.step >= 2"
                :disabled="submitting"
                @click="openAuxiliary('ASSIGNED')"
                >重新指派责任人</NButton
              >
              <NButton :disabled="submitting" @click="openAuxiliary('COMMENTED')">补充说明</NButton>
            </NSpace>
          </NCard>
          <RiskLinkagePanel
            :key="`${detail.id}:${detailVersion}`"
            :risk-id="detail.id"
            :org-id="detail.orgId"
            :risk="detail"
            :closed="detail.status === 'CLOSED'"
            :can-manage="canManage"
            @pending="linkagePending = $event"
            ref="linkageRef"
            @changed="refreshRiskProgress"
            @show-origin="showOrigin"
          />
          <NCollapse v-model:expanded-names="expandedSections" class="detail-section">
            <NCollapseItem v-if="detail.sourceAlarm || detail.trend" name="source" title="来源告警与指标趋势">
              <NCard v-if="detail.sourceAlarm" size="small" title="来源告警" class="detail-section">
                <AlarmEventReference :event-id="detail.sourceAlarm.eventId" :label="detail.sourceAlarm.title || '查看来源采集事件'" />
                <div class="source-grid">
                  <span>指标字段</span
                  ><strong>{{ detail.sourceAlarm.metricCode || '--' }} / {{ detail.sourceAlarm.fieldCode }}</strong>
                  <span>触发值</span><strong>{{ detail.sourceAlarm.triggerValue || '--' }}</strong> <span>规则版本</span
                  ><AlarmRuleVersionLink :version-id="detail.sourceAlarm.ruleVersionId" :rule-id="detail.sourceAlarm.ruleId" :org-id="detail.orgId" /> <span>阈值条件</span
                  ><strong>{{ detail.sourceAlarm.conditionSnapshot || '--' }}</strong> <span>告警位置</span
                  ><LocationReference :location-id="detail.sourceAlarm.locationId" :name="detail.sourceAlarm.locationSnapshotPath" />
                </div>
              </NCard>
              <NCard v-if="detail.trend" size="small" title="触发指标趋势" class="detail-section">
                <MetricSeriesChart :data="detail.trend" height="300px" />
              </NCard>
            </NCollapseItem>
            <NCollapseItem name="history" :title="`处置记录（${detail.logs.length}）`">
              <NCard size="small" title="处置时间线" class="detail-section">
                <NTimeline v-if="detail.logs.length">
                  <NTimelineItem
                    v-for="log in detail.logs"
                    :key="log.id"
                    :id="`risk-log-${log.id}`"
                    :title="actionLabel(log.action)"
                    :time="formatDateTime(log.occurredAt)"
                  >
                    <div class="detail-description">
                      <template v-if="log.remark || log.result">{{ log.remark || log.result }}</template
                      ><HDictText v-else type="museum_safety_risk_status" :value="log.toStatus" />
                    </div>
                    <div v-if="log.assigneeId" class="log-meta">责任人：<UserText :value="log.assigneeId" /></div>
                    <NSpace v-if="log.attachments.length" class="log-meta"
                      ><NButton
                        v-for="(file, index) in log.attachments"
                        :key="file"
                        v-download="{ url: file, fileName: `处置附件 ${index + 1}`, preferServerFilename: true }"
                        text
                        type="primary"
                        >下载附件 {{ index + 1 }}</NButton
                      ></NSpace
                    >
                  </NTimelineItem>
                </NTimeline>
                <NEmpty v-else description="暂无处置记录" />
              </NCard>
            </NCollapseItem>
          </NCollapse>
        </template>
        <NEmpty v-else-if="!detailLoading" :description="id ? '风险详情未加载' : '请提供风险事件 ID'" />
      </NSpin>
      <template #form-action>
        <NButton
          v-if="canManage && detail && detail.status !== 'CLOSED' && primaryAction"
          type="primary"
          :loading="submitting"
          :disabled="!canSubmitPrimary"
          style="margin-right: 12px"
          @click="submitAction"
        >
          {{ actionLabel(primaryAction) }}
        </NButton>
      </template>
    </HPageForm>
    <HPageForm
      v-model:show="auxiliaryShow"
      :title="actionLabel(auxiliaryForm.action)"
      content-only
      :footer-style="{ paddingRight: '36px' }"
    >
      <NAlert v-if="actionError" type="error">{{ actionError }}</NAlert>
      <NForm label-placement="left" label-width="100">
        <NFormItem v-if="auxiliaryForm.action === 'ASSIGNED'" label="责任人" required
          ><UserSelect v-model:value="auxiliaryForm.assigneeId"
        /></NFormItem>
        <NFormItem label="说明" required
          ><NInput v-model:value="auxiliaryForm.remark" type="textarea" :maxlength="2000"
        /></NFormItem>
        <NFormItem label="附件"
          ><UploadFiles
            :key="auxiliaryGeneration"
            :form="auxiliaryForm"
            upload-field="attachments"
            module="museum-safety"
            list-type="text"
            label="上传附件"
            :disabled="submitting"
        /></NFormItem>
      </NForm>
      <template #form-action
        ><NButton
          type="primary"
          :loading="submitting"
          :disabled="
            appStore.uploading ||
            !auxiliaryForm.remark?.trim() ||
            (auxiliaryForm.action === 'ASSIGNED' && !auxiliaryForm.assigneeId)
          "
          style="margin-right: 12px"
          @click="submitAuxiliary"
          >提交</NButton
        ></template
      >
    </HPageForm>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue';
import {
  NAlert,
  NButton,
  NCard,
  NCollapse,
  NCollapseItem,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NSpace,
  NSpin,
  NSteps,
  NStep,
  NText,
  NTimeline,
  NTimelineItem,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import { useAppStore } from '@/store';
import UploadFiles from '@/components/form/field/upload/upload.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import UserText from '@/modules/sys/components/UserText.vue';
import AlarmRuleVersionLink from '@/modules/museum-device/components/AlarmRuleVersionLink.vue';
import AlarmEventReference from '@/modules/museum-device/components/AlarmEventReference.vue';
import DeviceReference from '@/modules/museum-device/components/DeviceReference.vue';
import LocationReference from '@/modules/museum-device/components/LocationReference.vue';
import MetricSeriesChart from '../../museum-device/components/MetricSeriesChart.vue';
import { loadVenueCatalog } from '../../museum-device/utils/venueCatalog';
import type { MuseumVenueVo } from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type { SafetyRiskActionCommand, SafetyRiskVo } from '../typings';
import RiskLinkagePanel from './RiskLinkagePanel.vue';

const props = withDefaults(defineProps<{ id?: string | null; show?: boolean; readonly?: boolean }>(), {
  id: '',
  show: false,
  readonly: false,
});
const emit = defineEmits<{
  'update:show': [show: boolean];
  saved: [id: string];
}>();
const visible = computed({ get: () => props.show, set: (value) => emit('update:show', value) });
const authStore = useAuthStore(),
  appStore = useAppStore(),
  message = useMessage();
const canSelect = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_SELECT'));
const canManage = computed(() => !props.readonly && authStore.hasPermission('ADMIN,MUSEUM_SAFETY_MANAGE'));
const expandedSections = ref<string[]>([]);
const linkageRef = ref<{ refresh: () => Promise<unknown> }>();
const detail = ref<SafetyRiskVo | null>(null),
  venues = ref<MuseumVenueVo[]>([]);
const detailLoading = ref(false),
  detailVersion = ref(0),
  detailError = ref('');
const linkagePending = ref<number | null>(null),
  submitting = ref(false);
const attachmentGeneration = ref(0),
  auxiliaryShow = ref(false),
  auxiliaryGeneration = ref(0),
  actionError = ref('');
const actionForm = reactive<SafetyRiskActionCommand>({ action: 'CONFIRMED', attachments: [] });
const auxiliaryForm = reactive<SafetyRiskActionCommand>({ action: 'COMMENTED', remark: '', attachments: [] });
let session = 0,
  detailRequest = 0;
const current = (token: number, id: string) => token === session && props.show && props.id === id && canSelect.value;
const locationMoved = computed(
  () => detail.value && detail.value.locationSnapshotId !== detail.value.currentLocationId,
);
const primaryAction = computed(() => detail.value?.workflow?.nextAction || null);
const primaryBlocked = computed(() => {
  const risk = detail.value,
    step = risk?.workflow?.step;
  if (step === undefined) return '处置进度未加载，请刷新详情。';
  if (step >= 2 && step <= 5 && linkagePending.value !== 0)
    return linkagePending.value === null
      ? '正在核对联动任务进度。'
      : `尚有 ${linkagePending.value} 项联动任务未完成，请先执行并复核，或说明取消原因。`;
  if (step === 3 && risk?.sourceType === 'ALARM') return '等待采集系统确认来源告警恢复。';
  return risk?.workflow?.blockedReason || null;
});
const canSubmitPrimary = computed(() =>
  Boolean(
    canManage.value &&
      primaryAction.value &&
      !detailLoading.value &&
      !submitting.value &&
      !appStore.uploading &&
      !primaryBlocked.value &&
      (primaryAction.value !== 'ASSIGNED' || actionForm.assigneeId) &&
      (primaryAction.value !== 'PROCESSED' || actionForm.result?.trim()),
  ),
);
function resetAction() {
  Object.assign(actionForm, {
    action: primaryAction.value || 'COMMENTED',
    expectedTriggerLogId: detail.value?.workflow?.triggerLogId,
    assigneeId: detail.value?.assigneeId || null,
    result: null,
    remark: null,
    attachments: [],
  });
  attachmentGeneration.value++;
}
async function loadDetail(resetForm = true) {
  const id = props.id;
  if (!id || !props.show || !canSelect.value) return;
  const token = session,
    request = ++detailRequest;
  detailLoading.value = true;
  if (resetForm || detail.value?.id !== id) linkagePending.value = null;
  detailError.value = '';
  try {
    const value = unwrapResponse<SafetyRiskVo>(await safetyApi.risk(id));
    if (!current(token, id) || request !== detailRequest) return;
    if (!value?.id || value.id !== id) throw new Error('风险详情与请求事件不一致，请重新加载。');
    if (resetForm) detailVersion.value++;
    detail.value = {
      ...value,
      logs: (value.logs || []).map((log) => ({ ...log, attachments: log.attachments || [] })),
    };
    if (resetForm) resetAction();
    else if (
      actionForm.expectedTriggerLogId !== detail.value.workflow?.triggerLogId ||
      actionForm.action !== (primaryAction.value || 'COMMENTED')
    ) {
      resetAction();
      auxiliaryShow.value = false;
      actionError.value = '事件处置阶段已更新，请按当前步骤办理。';
    }
  } catch (cause: any) {
    if (!current(token, id) || request !== detailRequest) return;
    detail.value = null;
    detailError.value = cause?.message || '风险详情加载失败。';
  } finally {
    if (current(token, id) && request === detailRequest) detailLoading.value = false;
  }
}
function openAuxiliary(action: 'ASSIGNED' | 'COMMENTED') {
  if (!canManage.value || !detail.value) return;
  actionError.value = '';
  Object.assign(auxiliaryForm, {
    action,
    expectedTriggerLogId: detail.value.workflow?.triggerLogId,
    assigneeId: detail.value.assigneeId || null,
    remark: '',
    attachments: [],
  });
  auxiliaryGeneration.value++;
  auxiliaryShow.value = true;
}
async function submitAuxiliary() {
  const token = session;
  if ((await submitRiskCommand({ ...auxiliaryForm })) && token === session) auxiliaryShow.value = false;
}
async function submitAction() {
  if (!canSubmitPrimary.value || !primaryAction.value) return;
  await submitRiskCommand({
    ...actionForm,
    action: primaryAction.value,
    assigneeId: primaryAction.value === 'ASSIGNED' ? actionForm.assigneeId : null,
  });
}
async function submitRiskCommand(command: SafetyRiskActionCommand): Promise<boolean> {
  const risk = detail.value,
    token = session;
  if (!risk || !current(token, risk.id) || !canManage.value || submitting.value || appStore.uploading) return false;
  submitting.value = true;
  actionError.value = '';
  try {
    await safetyApi.riskAction(risk.id, {
      ...command,
      expectedTriggerLogId: command.expectedTriggerLogId || risk.workflow?.triggerLogId,
      attachments: [...new Set(command.attachments || [])],
    });
    emit('saved', risk.id);
    if (current(token, risk.id)) {
      message.success('处置操作已记录');
      await loadDetail();
    }
    return true;
  } catch (cause: any) {
    if (current(token, risk.id)) actionError.value = cause?.message || '处置提交失败，表单内容已保留。';
    return false;
  } finally {
    submitting.value = false;
  }
}
async function refreshRiskProgress() {
  const id = detail.value?.id,
    token = session;
  if (!id || !current(token, id)) return;
  emit('saved', id);
  await loadDetail(false);
}
async function refreshDetail() {
  if (submitting.value || appStore.uploading || detailLoading.value) return;
  const token = session,
    id = props.id;
  if (!id) return;
  await linkageRef.value?.refresh();
  if (current(token, id)) await loadDetail(false);
}
async function showOrigin(logId: string) {
  if (!expandedSections.value.includes('history')) expandedSections.value.push('history');
  await nextTick();
  document.getElementById(`risk-log-${logId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}
function invalidate() {
  ++session;
  ++detailRequest;
  expandedSections.value = [];
  detail.value = null;
  detailLoading.value = false;
  detailError.value = '';
  linkagePending.value = null;
  actionError.value = '';
  auxiliaryShow.value = false;
  Object.assign(auxiliaryForm, { action: 'COMMENTED', assigneeId: null, remark: '', result: null, attachments: [] });
  resetAction();
}
watch(
  () => [props.show, props.id, canSelect.value],
  () => {
    invalidate();
    if (!props.show || !props.id || !canSelect.value) return;
    const id = props.id,
      token = session;
    void loadVenueCatalog()
      .then((value) => {
        if (current(token, id)) venues.value = value;
      })
      .catch(() => {});
    void loadDetail();
  },
  { immediate: true },
);
onBeforeUnmount(invalidate);
onDeactivated(() => {
  invalidate();
  emit('update:show', false);
});
function venueName(value: string) {
  const venue = venues.value.find((item) => item.id === value);
  return venue?.shortName || venue?.name || value;
}
function domainLabel(value: string) {
  return (
    (
      {
        BUILDING: '建筑结构',
        LIGHTNING: '防雷',
        FIRE: '消防',
        SECURITY: '安防',
        ELECTRICITY: '用电',
        BUILDING_ENVIRONMENT: '建筑环境',
      } as Record<string, string>
    )[value] || value
  );
}
function actionLabel(value: string) {
  return (
    (
      {
        TRIGGERED: '风险触发',
        UPDATED: '来源更新',
        CONFIRMED: '确认风险',
        ASSIGNED: '指派责任人',
        PROCESSED: '提交处置结果',
        RECOVERED: '标记现场已恢复',
        RECOVERY_CONFIRMED: '确认恢复',
        CLOSED: '关闭风险',
        COMMENTED: '补充说明',
        LINKAGE_CREATED: '发起事件联动',
      } as Record<string, string>
    )[value] || value
  );
}
defineExpose({ refresh: loadDetail });
</script>

<style scoped>
.detail-description {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.detail-section {
  margin-top: 16px;
}
.source-grid {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 10px;
}
.source-grid span,
.log-meta {
  color: #7a8494;
}
.log-meta {
  margin-top: 4px;
  font-size: 12px;
}
</style>
