<template>
  <HPageForm
    :show="show"
    title="预警详情与处置"
    content-only
    :footer-style="{ paddingRight: '36px' }"
    cancel-text="关闭"
    @update:show="emit('update:show', $event)"
  >
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <NSpin :show="loading">
      <template v-if="detail">
        <NDescriptions :column="2" bordered label-placement="left">
          <NDescriptionsItem label="预警" :span="2">{{ detail.event.title }}</NDescriptionsItem>
          <NDescriptionsItem label="来源类别" :span="2">{{ detail.event.sourceCategory ? ALARM_SOURCE_CATEGORY_LABELS[detail.event.sourceCategory] : '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="设备"><DeviceReference :device-id="detail.event.deviceId" :name="detail.deviceName || detail.event.deviceName" /></NDescriptionsItem>
          <NDescriptionsItem label="触发位置"><LocationReference :location-id="detail.event.locationId" :name="detail.event.locationSnapshotPath" /></NDescriptionsItem>
          <NDescriptionsItem label="当前等级"
            ><HDictText type="museum_alarm_severity" :value="detail.event.currentSeverity || detail.event.severity"
          /></NDescriptionsItem>
          <NDescriptionsItem label="峰值等级"
            ><HDictText type="museum_alarm_severity" :value="detail.event.severity"
          /></NDescriptionsItem>
          <NDescriptionsItem label="采集状态"
            ><HDictText type="museum_alarm_lifecycle_status" :value="detail.event.lifecycleStatus"
          /></NDescriptionsItem>
          <NDescriptionsItem label="结束原因">{{ detail.event.terminationReason || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="首次发生">{{ formatDateTime(detail.event.startedAt) }}</NDescriptionsItem>
          <NDescriptionsItem label="最近发生">{{ formatDateTime(detail.event.lastOccurredAt) }}</NDescriptionsItem>
          <NDescriptionsItem label="发生次数">{{ detail.event.occurrenceCount }}</NDescriptionsItem>
          <NDescriptionsItem label="结束时间">{{
            detail.event.endedAt ? formatDateTime(detail.event.endedAt) : '--'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="触发规则版本"><AlarmRuleVersionLink
            :version-id="detail.event.triggerRuleVersionId" :rule-id="detail.event.ruleId" :org-id="detail.event.orgId" :branch-id="detail.event.triggerBranchId"
            :rule-name="detail.event.triggerRuleVersion?.ruleName" :version-no="detail.event.triggerRuleVersion?.versionNo"
            :placeholder="detail.event.source === 'DEVICE' ? '设备直报' : '未关联规则版本'" /></NDescriptionsItem>
          <NDescriptionsItem label="当前规则版本"><AlarmRuleVersionLink
            :version-id="detail.event.currentRuleVersionId" :rule-id="detail.event.ruleId" :org-id="detail.event.orgId" :branch-id="detail.event.currentBranchId"
            :rule-name="detail.event.currentRuleVersion?.ruleName" :version-no="detail.event.currentRuleVersion?.versionNo" /></NDescriptionsItem>
          <NDescriptionsItem label="触发值">{{ formatMetricValue({ stringValue: detail.event.triggerValue, unit: detail.event.triggerUnit }) }}</NDescriptionsItem>
          <NDescriptionsItem label="指标字段">{{ detail.event.fieldCode || 'VALUE' }}</NDescriptionsItem>
        </NDescriptions>
        <NCard size="small" title="处置进度" style="margin-top: 16px">
          <NSteps :current="handlingStep" :status="handlingStep === 4 ? 'finish' : 'process'" size="small">
            <NStep title="确认预警" /><NStep title="开始处理" /><NStep title="登记处理结果" />
          </NSteps>
          <NText depth="3">采集状态由后续监测数据更新，实际措施和结果在此登记。</NText>
          <NAlert v-if="progress.externallyCompleted" type="success" :show-icon="false" style="margin-top: 12px">外部处置完成，可补充说明。</NAlert>
          <NAlert v-else-if="detail.handling?.flowStatus === 'RUNNING'" type="info" :show-icon="false" style="margin-top: 12px">外部流程办理中，完成后同步处置结果；当前可补充说明。</NAlert>
          <NDescriptions v-if="detail.handling" :column="2" label-placement="left" style="margin-top: 12px">
            <NDescriptionsItem label="处置状态"><HDictText type="museum_alarm_handling_status" :value="detail.handling.status" /></NDescriptionsItem>
            <NDescriptionsItem v-if="detail.handling.occurredAt" label="处置时间">{{ formatDateTime(detail.handling.occurredAt) }}</NDescriptionsItem>
            <NDescriptionsItem v-if="detail.handling.operatorName" label="处置人">
              <span v-if="detail.handling.sourceCode && detail.handling.sourceCode !== 'LOCAL'">{{ detail.handling.operatorName }}</span>
              <UserText v-else :value="detail.handling.operatorId" :name="detail.handling.operatorName" />
            </NDescriptionsItem>
            <NDescriptionsItem v-if="detail.handling.sourceCode && detail.handling.sourceCode !== 'LOCAL'" label="处置来源">{{ detail.handling.sourceCode }}</NDescriptionsItem>
            <NDescriptionsItem v-if="detail.handling.externalId" label="外部单号">{{ detail.handling.externalId }}</NDescriptionsItem>
            <NDescriptionsItem v-if="detail.handling.flowInstanceId" label="外部流程">{{ detail.handling.flowInstanceId }}</NDescriptionsItem>
            <NDescriptionsItem v-if="detail.handling.flowStatus" label="流程状态"><HDictText type="museum_alarm_handling_flow_status" :value="detail.handling.flowStatus" /></NDescriptionsItem>
            <NDescriptionsItem v-if="detail.handling.reason" label="处置结果" :span="2">{{ detail.handling.reason }}</NDescriptionsItem>
            <NDescriptionsItem v-if="Object.keys(detail.handling.details || {}).length" label="外部处置信息" :span="2"><pre class="snapshot">{{ JSON.stringify(detail.handling.details, null, 2) }}</pre></NDescriptionsItem>
          </NDescriptions>
        </NCard>
        <NForm v-if="canManage" label-placement="left" label-width="90" style="margin-top: 16px">
          <NFormItem label="当前操作"
            ><NText>{{ submitLabel }}</NText></NFormItem
          >
          <NFormItem label="措施 / 结果" required
            ><NInput v-model:value="form.reason" type="textarea" :maxlength="2000" show-count :disabled="saving"
          /></NFormItem>
        </NForm>
        <NButton v-if="canManage && progress.action !== 'COMMENTED'" text type="primary" :disabled="saving || loading" @click="toggleComment">{{
          form.action === 'COMMENTED' ? '返回当前处置' : '补充说明'
        }}</NButton>
        <NCollapse style="margin-top: 16px">
          <NCollapseItem name="history" :title="`处置记录（${detail.handlingRecords.length}）`">
            <NTimeline v-if="detail.handlingRecords.length">
              <NTimelineItem
                v-for="record in detail.handlingRecords"
                :key="record.id"
                :title="`${recordLabel(record)}${record.applied === false ? '（未应用）' : ''}`"
                :time="formatDateTime(record.occurredAt)"
              >
                <div class="long-text">{{ record.reason }}</div>
                <NText depth="3">
                  <span v-if="record.sourceCode && record.sourceCode !== 'LOCAL'">{{ record.operatorName }} · {{ record.sourceCode }}<template v-if="record.externalId"> · {{ record.externalId }}</template></span>
                  <UserText v-else :value="record.operatorId" :name="record.operatorName" />
                </NText>
              </NTimelineItem>
            </NTimeline>
            <NEmpty v-else description="暂无采集事件处置记录" />
          </NCollapseItem>
          <NCollapseItem name="source" title="规则条件与原始事件详情">
            <NDescriptions :column="1" bordered label-placement="left">
              <NDescriptionsItem label="来源">{{ detail.event.source === 'DEVICE' ? '设备直报' : '规则引擎' }}</NDescriptionsItem>
              <NDescriptionsItem label="预警编码">{{ detail.event.alarmCode || '--' }}</NDescriptionsItem>
              <NDescriptionsItem label="规则条件" :span="2">
                <pre class="snapshot">{{ jsonText(detail.event.conditionSnapshot) }}</pre>
              </NDescriptionsItem>
              <NDescriptionsItem label="事件详情" :span="2">
                <pre class="snapshot">{{ jsonText(detail.event.detailsJson) }}</pre>
              </NDescriptionsItem>
            </NDescriptions>
          </NCollapseItem>
        </NCollapse>
      </template>
    </NSpin>
    <template #form-action>
      <NButton
        v-if="canManage && detail"
        type="primary"
        :loading="saving"
        :disabled="!form.reason.trim() || loading || saving"
        style="margin-right: 12px"
        @click="submit"
        >{{ submitLabel }}</NButton
      >
    </template>
  </HPageForm>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue';
import { ingestApi } from '../api/ingest';
import type { AlarmEventVo } from '../typings';
import { formatDateTime } from '@/utils';
import { formatMetricValue } from '../utils/metricValue';
import { ALARM_SOURCE_CATEGORY_LABELS } from '../utils/ingestDict';
import { alarmHandlingProgress, type AlarmHandlingState } from '../utils/alarmHandlingProgress';
import AlarmRuleVersionLink from './AlarmRuleVersionLink.vue';
import DeviceReference from './DeviceReference.vue';
import LocationReference from './LocationReference.vue';
import UserText from '@/modules/sys/components/UserText.vue';
const props = defineProps<{ show: boolean; eventId: string; canManage: boolean }>();
const emit = defineEmits(['update:show', 'saved']);
interface HandlingRecord {
  id: string;
  action: string;
  reason: string;
  operatorId: string;
  operatorName: string;
  occurredAt: number;
  sourceCode?: string;
  externalId?: string;
  applied?: boolean;
}
const detail = ref<{ event: AlarmEventVo; deviceName?: string; handlingRecords: HandlingRecord[]; handling?: AlarmHandlingState } | null>(null);
const loading = ref(false),
  saving = ref(false),
  error = ref('');
const form = reactive({ action: 'CONFIRMED', reason: '' });
const actions = [
  { label: '确认预警', value: 'CONFIRMED' },
  { label: '开始处理', value: 'PROCESSING' },
  { label: '登记处理结果', value: 'PROCESSED' },
  { label: '补充说明', value: 'COMMENTED' },
];
const progress = computed(() => alarmHandlingProgress(detail.value?.handlingRecords || [], detail.value?.handling));
const handlingStep = computed(() => progress.value.step);
const submitLabel = computed(() => (form.action === 'COMMENTED' ? '补充说明' : progress.value.label));
function toggleComment() {
  form.action = form.action === 'COMMENTED' ? progress.value.action : 'COMMENTED';
}
let generation = 0;
function recordLabel(record: HandlingRecord) {
  if (record.action === 'PROCESSED' && record.sourceCode && record.sourceCode !== 'LOCAL') return '外部处置完成';
  return actions.find((item) => item.value === record.action)?.label || record.action;
}
function jsonText(value?: string | null) {
  if (!value) return '--';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
async function load() {
  const token = ++generation;
  detail.value = null;
  error.value = '';
  loading.value = true;
  try {
    const response: any = await ingestApi.alarmDetail(props.eventId);
    if (token === generation) {
      const value = response?.data?.data ?? response?.data ?? response;
      if (value?.event?.id !== props.eventId) throw new Error('预警详情与请求事件不一致，请重新读取');
      detail.value = { ...value, handlingRecords: value.handlingRecords || [] };
      form.action = progress.value.action;
    }
  } catch (cause: any) {
    if (token === generation) error.value = cause?.message || '详情读取失败，请重新打开重试';
  } finally {
    if (token === generation) loading.value = false;
  }
}
watch(
  () => [props.show, props.eventId],
  () => {
    ++generation;
    detail.value = null;
    error.value = '';
    loading.value = false;
    form.action = 'CONFIRMED';
    form.reason = '';
    if (props.show && props.eventId) void load();
  },
  { immediate: true },
);
async function submit() {
  if (!props.show || !detail.value || loading.value || !props.canManage || saving.value || !form.reason.trim()) return;
  const id = props.eventId,
    token = generation;
  saving.value = true;
  error.value = '';
  try {
    await ingestApi.handleAlarm(id, { ...form });
    emit('saved');
    if (token === generation && props.show) {
      form.reason = '';
      await load();
    }
  } catch (cause: any) {
    if (token === generation) error.value = cause?.message || '处置提交失败，内容已保留';
  } finally {
    saving.value = false;
  }
}
onDeactivated(() => {
  ++generation;
  detail.value = null;
  loading.value = false;
  error.value = '';
  emit('update:show', false);
});
onBeforeUnmount(() => {
  ++generation;
});
</script>
<style scoped>
.long-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.snapshot {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 0;
  max-height: 240px;
  overflow: auto;
}
</style>
