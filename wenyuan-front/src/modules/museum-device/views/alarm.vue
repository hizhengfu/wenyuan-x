<template>
  <div class="alarm-page">
    <NTabs v-model:value="activeTab" type="line" animated class="alarm-tabs"
      pane-wrapper-style="display: flex; flex: 1; min-height: 0; overflow: hidden;"
      pane-style="display: flex; flex: 1; min-height: 0; flex-direction: column; overflow: hidden;">
      <NTabPane name="events" tab="预警事件" display-directive="show">
        <HPageDataTable ref="eventTableRef" title="预警事件" class="alarm-data-table" :columns="eventColumns"
          :fetch-data="loadAlarmEvents" :params="eventQuery" :scroll-x="1990" row-key-field="id"
          :pagination="{ showQuickJumper: false }" :reset-page-on-size-change="true"
          :default-visible-column-keys="eventDefaultColumns" :always-visible-column-keys="['actions']"
          :is-add="false" :is-edit="false" :is-copy="false" :is-delete="false" :is-export="false" :show-operate="false">
          <template #queryForm>
            <NFormItem label="生命周期"><NSelect v-model:value="eventQuery.lifecycleStatus" :options="lifecycleOptions" clearable style="width: 150px" /></NFormItem>
            <NFormItem label="当前等级"><NSelect v-model:value="eventQuery.severity" :options="severityOptions" clearable style="width: 140px" /></NFormItem>
            <NFormItem label="来源类别"><NSelect v-model:value="eventQuery.sourceCategory" :options="ALARM_SOURCE_CATEGORY_OPTIONS" clearable style="width: 180px" /></NFormItem>
            <NFormItem label="设备 ID"><NInput v-model:value="eventQuery.deviceId" clearable style="width: 210px" /></NFormItem>
          </template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane name="rules" tab="预警规则" display-directive="show:lazy">
        <HPageDataTable ref="ruleTableRef" title="预警规则" class="alarm-data-table" :columns="ruleColumns"
          :fetch-data="ingestApi.alarmRules" :scroll-x="1790" row-key-field="id" :is-add="canManage"
          :default-visible-column-keys="ruleDefaultColumns" :always-visible-column-keys="['actions']"
          :is-edit="false" :is-copy="false" :is-delete="false" :is-export="false" :show-operate="false" @add="openRule()" />
      </NTabPane>
      <NTabPane v-if="canManage" name="sms" tab="短信通知" display-directive="show:lazy">
        <AlarmSmsPanel />
      </NTabPane>
    </NTabs>

    <AlarmEventDetail v-model:show="eventShow" :event-id="eventId" :can-manage="canManage" @saved="eventTableRef?.handleRefresh()" />
    <NModal v-model:show="ruleShow" preset="card" :title="ruleForm.id ? '编辑人工预警规则' : '新增人工预警规则'"
      style="width: min(1080px, calc(100vw - 32px)); max-height: calc(100dvh - 32px);"
      :header-style="{ flexShrink: 0 }"
      :content-style="{ minHeight: 0, overflowY: 'auto' }"
      :footer-style="{ flexShrink: 0, paddingTop: '16px', paddingRight: '36px' }">
      <NAlert type="info" :bordered="false" style="margin-bottom: 14px">
        一个规则版本可以包含多个分支：满足任一分支即触发；同一分支中的多个条件必须全部满足；同时命中多个分支时取最高等级。
      </NAlert>
      <NForm label-placement="left" label-width="110">
        <NGrid :cols="2" :x-gap="16">
          <NFormItemGi label="所属场馆" required :span="2">
            <VenueSelect :value="ruleForm.orgId" :disabled="Boolean(ruleForm.id)" placeholder="请选择所属场馆" @update:value="onRuleOrgUpdate" />
          </NFormItemGi>
          <NFormItemGi label="规则编码" required><NInput v-model:value="ruleForm.code" placeholder="如 TEMPERATURE_LIMIT" /></NFormItemGi>
          <NFormItemGi label="规则名称" required><NInput v-model:value="ruleForm.name" /></NFormItemGi>
          <NFormItemGi label="规则类型" required><NSelect v-model:value="ruleForm.ruleType" :options="ruleTypeOptions" @update:value="onRuleTypeUpdate" /></NFormItemGi>
          <NFormItemGi label="运行状态" required><NSelect v-model:value="ruleForm.runtimeStatus" :options="runtimeStatusOptions" /></NFormItemGi>
          <NFormItemGi label="事实类型"><NSelect v-model:value="ruleForm.recordType" :options="recordTypeOptions" disabled /></NFormItemGi>
          <NFormItemGi v-if="ruleForm.ruleType !== 'DEVICE_OFFLINE'" label="标准指标" required>
            <NSelect v-model:value="ruleForm.recordKey" :options="recordKeyOptions" filterable placeholder="请选择已启用的标准指标" @update:value="onRecordKeyUpdate" />
          </NFormItemGi>
          <NFormItemGi label="设备范围">
            <NSelect v-model:value="ruleForm.deviceId" :options="deviceOptions" :loading="scopeCatalogLoading" :disabled="!ruleForm.orgId"
              :filter="filterScopeOption" filterable clearable placeholder="留空匹配本场馆全部设备" />
          </NFormItemGi>
          <NFormItemGi label="位置范围">
            <NSelect v-model:value="ruleForm.locationId" :options="locationOptions" :loading="scopeCatalogLoading" :disabled="!ruleForm.orgId"
              :filter="filterScopeOption" filterable clearable placeholder="留空匹配本场馆全部位置" />
          </NFormItemGi>
          <NFormItemGi label="连续触发次数"><NInputNumber v-model:value="ruleForm.triggerCount" :min="1" :max="10000" style="width: 100%" /></NFormItemGi>
          <NFormItemGi label="连续恢复次数"><NInputNumber v-model:value="ruleForm.recoveryCount" :min="1" :max="10000" style="width: 100%" /></NFormItemGi>
          <NFormItemGi label="规则依据" :span="2"><NInput v-model:value="ruleForm.standardRef" placeholder="标准编号或规则依据" /></NFormItemGi>
          <NFormItemGi label="消息模板" :span="2"><NInput v-model:value="ruleForm.messageTemplate" type="textarea" :rows="2" /></NFormItemGi>
          <NFormItemGi label="历史回灌触发"><NSwitch v-model:value="ruleForm.includeBackfill" /></NFormItemGi>
        </NGrid>

        <NSpace justify="space-between" align="center"><NDivider title-placement="left">条件分支</NDivider><NButton size="small" @click="addBranch">新增分支</NButton></NSpace>
        <NCard v-for="(branch, branchIndex) in editableBranches" :key="branchIndex" size="small" class="branch-card">
          <template #header>分支 {{ branchIndex + 1 }}</template>
          <template #header-extra><NButton text type="error" :disabled="editableBranches.length <= 1" @click="removeBranch(branchIndex)">移除分支</NButton></template>
          <NGrid :cols="3" :x-gap="12">
            <NFormItemGi label="分支编码" required><NInput v-model:value="branch.code" /></NFormItemGi>
            <NFormItemGi label="分支名称" required><NInput v-model:value="branch.name" /></NFormItemGi>
            <NFormItemGi label="预警等级" required><NSelect v-model:value="branch.severity" :options="severityOptions" /></NFormItemGi>
          </NGrid>
          <div v-for="(condition, conditionIndex) in branch.conditions" :key="conditionIndex" class="condition-row">
            <NGrid :cols="5" :x-gap="12" align-items="end">
              <NFormItemGi v-if="ruleForm.ruleType === 'VALUE_CONDITION'" label="指标字段">
                <NSelect v-model:value="condition.fieldCode" :options="fieldOptions" :disabled="!ruleForm.recordKey" placeholder="选择字段" />
              </NFormItemGi>
              <NFormItemGi :label="`条件 ${conditionIndex + 1}`"><NSelect v-model:value="condition.operator" :options="operatorOptions" @update:value="normalizeCondition(condition)" /></NFormItemGi>
              <NFormItemGi v-if="usesNumericCondition" :label="ruleForm.ruleType === 'DEVICE_OFFLINE' ? '离线时长（秒）' : '阈值一'">
                <NInputNumber v-model:value="condition.thresholdValue" style="width: 100%" />
              </NFormItemGi>
              <NFormItemGi v-if="usesSecondThreshold(condition)" label="阈值二"><NInputNumber v-model:value="condition.thresholdValue2" style="width: 100%" /></NFormItemGi>
              <NFormItemGi v-if="usesExpectedValue" label="期望/异常值"><NInput v-model:value="condition.expectedValue" /></NFormItemGi>
              <NFormItemGi><NButton v-if="ruleForm.ruleType !== 'DEVICE_OFFLINE'" text type="error" :disabled="branch.conditions.length <= 1"
                @click="branch.conditions.splice(conditionIndex, 1)">移除条件</NButton></NFormItemGi>
            </NGrid>
          </div>
          <NButton v-if="ruleForm.ruleType !== 'DEVICE_OFFLINE'" size="tiny" dashed @click="addCondition(branchIndex)">新增 AND 条件</NButton>
        </NCard>
      </NForm>
      <template #footer><NSpace justify="end"><NButton @click="ruleShow = false">取消</NButton><NButton type="primary" :loading="ruleSaving" @click="saveRule">发布新版本</NButton></NSpace></template>
    </NModal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NAlert, NButton, NCard, NDivider, NForm, NFormItem, NFormItemGi, NGrid, NInput, NInputNumber, NModal,
  NSelect, NSpace, NSwitch, NTabPane, NTabs, NTag, type DataTableColumns, type EllipsisProps, type SelectOption, useDialog, useMessage } from 'naive-ui';
import { formatDateTime } from '@/utils';
import { formatMetricValue } from '../utils/metricValue';
import { useDict } from '@/utils/dict';
import { useAuthStore } from '@/store';
import { ingestApi } from '../api/ingest';
import { createAlarmEventLoader } from '../utils/alarmEventLoader';
import { HistoryCursorPager } from '../utils/historyCursorPager';
import type { AlarmSourceCategory, AlarmEventVo, AlarmOperator, AlarmRuleBranchVo, AlarmRuleConditionVo, AlarmRuleType, AlarmRuleVo, DeviceVo,
  MuseumLocationManageVo, StandardMetricDefinition } from '../typings';
import { ALARM_SOURCE_CATEGORY_LABELS, ALARM_SOURCE_CATEGORY_OPTIONS, INGEST_RECORD_TYPE_DICT } from '../utils/ingestDict';
import VenueSelect from '../components/VenueSelect.vue';
import VenueText from '../components/VenueText.vue';
import AlarmSmsPanel from '../components/AlarmSmsPanel.vue';
import AlarmEventDetail from '../components/AlarmEventDetail.vue';
import AlarmRuleVersionLink from '../components/AlarmRuleVersionLink.vue';
import DeviceReference from '../components/DeviceReference.vue';
import LocationReference from '../components/LocationReference.vue';

const eventPager = new HistoryCursorPager<AlarmEventVo>();
const loadAlarmEvents = createAlarmEventLoader((params) => {
  const { pageNum, pageSize, ...filters } = params;
  return eventPager.fetch({ pageNum: Number(pageNum), pageSize: Number(pageSize) }, filters, async (query) => {
    const response: any = await ingestApi.alarmWindow(query);
    return response?.data?.data ?? response?.data ?? response;
  });
});
onDeactivated(loadAlarmEvents.cancelPending);
onBeforeUnmount(loadAlarmEvents.cancelPending);

const message = useMessage();
const dialog = useDialog();
const authStore = useAuthStore();
const canManage = computed(() => authStore.hasPermission('ADMIN,MUSEUM_DEVICE_ADMIN,MUSEUM_DEVICE_MANAGE'));
const activeTab = ref('events');
const eventShow = ref(false);
const eventId = ref('');
onDeactivated(() => { eventShow.value = false; });
function openEvent(row: AlarmEventVo) { eventId.value = row.id; eventShow.value = true; }
const eventTableRef = ref<any>(null);
const ruleTableRef = ref<any>(null);
const eventQuery = reactive({ lifecycleStatus: null as string | null, severity: null as string | null,
  sourceCategory: null as AlarmSourceCategory | null, deviceId: null as string | null });
const ruleShow = ref(false);
const ruleSaving = ref(false);
const standardMetrics = ref<StandardMetricDefinition[]>([]);
const scopeDevices = ref<DeviceVo[]>([]);
const scopeLocations = ref<MuseumLocationManageVo[]>([]);
const scopeCatalogLoading = ref(false);
let scopeCatalogRequestId = 0;
let branchSequence = 0;

function createCondition(type: AlarmRuleType = 'VALUE_CONDITION'): AlarmRuleConditionVo {
  if (type === 'DEVICE_OFFLINE') return { recordType: 'DEVICE_HEALTH', recordKey: 'DEVICE_OFFLINE', operator: 'GT', thresholdValue: 300 };
  if (type === 'STATE_CONDITION' || type === 'DATA_QUALITY') return { operator: 'EQ', expectedValue: '' };
  return { operator: 'GT', thresholdValue: null };
}
function createBranch(type: AlarmRuleType = 'VALUE_CONDITION'): AlarmRuleBranchVo {
  branchSequence += 1;
  return { code: `BRANCH_${branchSequence}`, name: `条件分支 ${branchSequence}`, severity: 'WARNING', priority: branchSequence, conditions: [createCondition(type)] };
}
function createDefaultRule(): AlarmRuleVo {
  return { ruleType: 'VALUE_CONDITION', recordType: 'TELEMETRY', runtimeStatus: 'ACTIVE', triggerCount: 1, recoveryCount: 1,
    includeBackfill: false, variants: [{ code: 'GENERAL', name: '通用', priority: 0, branches: [createBranch()] }] };
}
const ruleForm = ref<AlarmRuleVo>(createDefaultRule());
const editableBranches = computed(() => ruleForm.value.variants[0]?.branches || []);
const severityOptions = [{ label: '提示', value: 'NOTICE' }, { label: '警告', value: 'WARNING' }, { label: '严重', value: 'MAJOR' }, { label: '紧急', value: 'CRITICAL' }];
const lifecycleOptions = [{ label: '打开', value: 'OPEN' }, { label: '已关闭', value: 'CLOSED' }];
const runtimeStatusOptions = [{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'SUSPENDED' }, { label: '归档', value: 'ARCHIVED' }];
const ruleTypeOptions = [{ label: '数值条件', value: 'VALUE_CONDITION' }, { label: '状态条件', value: 'STATE_CONDITION' },
  { label: '数据质量', value: 'DATA_QUALITY' }, { label: '设备离线', value: 'DEVICE_OFFLINE' }];
const { options: recordTypeOptions } = useDict(INGEST_RECORD_TYPE_DICT);
const numericOperatorOptions = [{ label: '大于', value: 'GT' }, { label: '大于等于', value: 'GTE' }, { label: '小于', value: 'LT' },
  { label: '小于等于', value: 'LTE' }, { label: '等于', value: 'EQ' }, { label: '不等于', value: 'NE' },
  { label: '区间内', value: 'BETWEEN' }, { label: '区间外', value: 'OUTSIDE' }];
const textOperatorOptions = [{ label: '等于', value: 'EQ' }, { label: '不等于', value: 'NE' }];
const severityTypes: Record<string, 'default' | 'warning' | 'error'> = { NOTICE: 'default', WARNING: 'warning', MAJOR: 'error', CRITICAL: 'error' };
const sourceLabels: Record<AlarmEventVo['source'], string> = { RULE: '规则引擎', DEVICE: '设备直报', VENUE: '场馆直报' };
const managedByLabels = { MANUAL: '人工规则', COLLECTION_PRESERVATION: '预防性保护编译' } as const;
const runtimeStatusLabels = { ACTIVE: '启用', SUSPENDED: '停用', ARCHIVED: '归档' } as const;
const selectedStandardMetric = computed(() => standardMetrics.value.find((metric) => metric.code === ruleForm.value.recordKey));
const recordKeyOptions = computed(() => standardMetrics.value.filter((metric) => metricSupportsRule(metric, ruleForm.value.ruleType || 'VALUE_CONDITION'))
  .map((metric) => ({ label: `${metric.name}（${metric.code}）${metric.unit ? ` · ${metric.unit}` : ''}${metric.enabled ? '' : ' [已停用]'}`,
    value: metric.code, disabled: !metric.enabled })).sort((a, b) => a.label.localeCompare(b.label, 'zh-CN')));
const deviceOptions = computed(() => scopeDevices.value.map((device) => ({ label: deviceLabel(device), value: device.id!,
  searchText: [device.id, device.name, device.code, device.externalDeviceId, device.locationPath].filter(Boolean).join(' ') })));
const locationOptions = computed(() => scopeLocations.value.map((location) => ({ label: locationLabel(location), value: location.id!,
  searchText: [location.id, location.name, location.code, location.path].filter(Boolean).join(' ') })));
const operatorOptions = computed(() => usesExpectedValue.value ? textOperatorOptions : numericOperatorOptions);
const usesNumericCondition = computed(() => ['VALUE_CONDITION', 'DEVICE_OFFLINE'].includes(ruleForm.value.ruleType || ''));
const usesExpectedValue = computed(() => ['STATE_CONDITION', 'DATA_QUALITY'].includes(ruleForm.value.ruleType || ''));
const fieldOptions = computed(() => (selectedStandardMetric.value?.valueFields || []).map((field) => ({
  label: `${field.name}（${field.code}）`,
  value: field.code,
})));

// 日常处置保留可判断风险、定位现场和追溯触发依据的列；时间明细、来源和次数可按需展开。
const eventDefaultColumns = ['orgId', 'title', 'sourceCategory', 'currentSeverity', 'severity', 'lifecycleStatus', 'triggerValue',
  'deviceId', 'locationSnapshotPath', 'triggerRuleVersionId', 'lastOccurredAt', 'actions'];
const ruleDefaultColumns = ['orgId', 'name', 'managedBy', 'versionNo', 'recordKey', 'variants', 'scope', 'runtimeStatus', 'actions'];
const textEllipsis: EllipsisProps = { tooltip: { contentStyle: { maxWidth: '520px', whiteSpace: 'normal', overflowWrap: 'anywhere' } } };
const eventColumns: DataTableColumns<AlarmEventVo> = [
  { title: '所属场馆', key: 'orgId', width: 170, fixed: 'left', ellipsis: textEllipsis,
    render: (row) => h(VenueText, { value: row.orgId }) },
  { title: '预警标题', key: 'title', width: 260, fixed: 'left', ellipsis: textEllipsis },
  { title: '来源类别', key: 'sourceCategory', width: 160, ellipsis: textEllipsis,
    render: (row) => row.sourceCategory ? ALARM_SOURCE_CATEGORY_LABELS[row.sourceCategory] : '--' },
  { title: '当前等级', key: 'currentSeverity', width: 100, render: (row) => severityTag(row.currentSeverity || row.severity) },
  { title: '峰值等级', key: 'severity', width: 100, render: (row) => severityTag(row.severity) },
  { title: '状态', key: 'lifecycleStatus', width: 100, ellipsis: textEllipsis,
    render: (row) => lifecycleOptions.find((item) => item.value === row.lifecycleStatus)?.label || row.lifecycleStatus },
  { title: '首次触发值', key: 'triggerValue', width: 150, ellipsis: textEllipsis, render: eventTriggerValue },
  { title: '设备', key: 'deviceId', width: 180, ellipsis: textEllipsis,
    render: (row) => h(DeviceReference, { deviceId: row.deviceId, name: row.deviceName }) },
  { title: '触发位置', key: 'locationSnapshotPath', width: 240, ellipsis: textEllipsis,
    render: (row) => h(LocationReference, { locationId: row.locationId, name: row.locationSnapshotPath }) },
  { title: '触发版本', key: 'triggerRuleVersionId', width: 220, ellipsis: textEllipsis,
    render: (row) => h(AlarmRuleVersionLink, { versionId: row.triggerRuleVersionId, ruleId: row.ruleId, orgId: row.orgId,
      branchId: row.triggerBranchId, ruleName: row.triggerRuleVersion?.ruleName, versionNo: row.triggerRuleVersion?.versionNo,
      placeholder: sourceLabels[row.source] }) },
  { title: '最近发生', key: 'lastOccurredAt', width: 185, ellipsis: textEllipsis, render: (row) => formatDateTime(row.lastOccurredAt) },
  { title: '首次发生', key: 'startedAt', width: 185, ellipsis: textEllipsis, render: (row) => formatDateTime(row.startedAt) },
  { title: '发生次数', key: 'occurrenceCount', width: 90 },
  { title: '来源', key: 'source', width: 110, ellipsis: textEllipsis, render: (row) => sourceLabels[row.source] || row.source },
  { title: '详情 / 处置', key: 'actions', width: 130, fixed: 'right', render: (row) =>
    h(NButton, { text: true, type: 'primary', onClick: () => openEvent(row) }, { default: () => canManage.value ? '详情 / 处置' : '详情' }) },
];
const ruleColumns: DataTableColumns<AlarmRuleVo> = [
  { title: '所属场馆', key: 'orgId', width: 170, fixed: 'left', ellipsis: textEllipsis,
    render: (row) => h(VenueText, { value: row.orgId }) },
  { title: '规则', key: 'name', width: 240, fixed: 'left', ellipsis: textEllipsis, render: (row) => row.name || row.code || '--' },
  { title: '管理来源', key: 'managedBy', width: 155, ellipsis: textEllipsis, render: (row) => managedByLabels[row.managedBy || 'MANUAL'] },
  { title: '版本', key: 'versionNo', width: 85, ellipsis: textEllipsis,
    render: (row) => h(AlarmRuleVersionLink, { versionId: row.activeVersionId, ruleId: row.id, orgId: row.orgId, versionNo: row.versionNo }) },
  { title: '监测指标', key: 'recordKey', width: 200, ellipsis: textEllipsis, render: recordKeyCell },
  { title: '条件分支', key: 'variants', width: 310, ellipsis: textEllipsis, render: branchSummaryCell },
  { title: '适用范围', key: 'scope', width: 260, ellipsis: textEllipsis, render: ruleScopeCell },
  { title: '状态', key: 'runtimeStatus', width: 90, render: runtimeStatusTag },
  { title: '类型', key: 'ruleType', width: 120, ellipsis: textEllipsis,
    render: (row) => ruleTypeOptions.find((item) => item.value === row.ruleType)?.label || row.ruleType },
  { title: '触发/恢复次数', key: 'count', width: 130, render: (row) => `${row.triggerCount || 1} / ${row.recoveryCount || 1}` },
  { title: '操作', key: 'actions', width: 180, fixed: 'right', render: ruleActions },
];

function eventTriggerValue(row: AlarmEventVo) {
  const value = formatMetricValue({ stringValue: row.triggerValue, unit: row.triggerUnit });
  return row.fieldCode && row.fieldCode !== 'VALUE' ? `${value}（${row.fieldCode}）` : value;
}

function severityTag(severity?: string) { return h(NTag, { size: 'small', type: severityTypes[severity || 'WARNING'], bordered: false },
  { default: () => severityOptions.find((item) => item.value === severity)?.label || severity || '--' }); }
function runtimeStatusTag(row: AlarmRuleVo) { const status = row.runtimeStatus || 'ACTIVE'; return h(NTag,
  { size: 'small', type: status === 'ACTIVE' ? 'success' : 'default', bordered: false }, { default: () => runtimeStatusLabels[status] }); }
function recordKeyCell(row: AlarmRuleVo) {
  const metric = standardMetrics.value.find((item) => item.code === row.recordKey);
  return row.ruleType === 'DEVICE_OFFLINE' ? '设备在线状态' : metric?.name || row.recordKey || '--';
}
function branchSummaryCell(row: AlarmRuleVo) {
  return row.variants.flatMap((variant) => variant.branches.map((branch) => {
    const name = [variant.name || variant.code, branch.name || branch.code].filter(Boolean).join(' / ');
    const conditions = branch.conditions.map((condition) => conditionText(condition, row)).join(' 且 ');
    const severity = severityOptions.find((item) => item.value === branch.severity)?.label || branch.severity;
    return `${name}：${conditions}（${severity}）`;
  })).join('；') || '--';
}
function conditionText(condition: AlarmRuleConditionVo, row: AlarmRuleVo) {
  const operator = operatorLabel(condition.operator);
  if (condition.expectedValue != null) return `${operator} ${condition.expectedValue || '--'}`;
  const metric = standardMetrics.value.find((item) => item.code === (condition.recordKey || row.recordKey));
  const unit = row.ruleType === 'DEVICE_OFFLINE' ? '秒' : metric?.unit || '';
  const values = condition.thresholdValue2 == null ? `${condition.thresholdValue ?? '--'}` : `${condition.thresholdValue ?? '--'} ～ ${condition.thresholdValue2}`;
  const field = metric?.valueFields.find((item) => item.code === condition.fieldCode)?.name || condition.fieldCode;
  return `${field ? `${field} ` : ''}${operator} ${values}${unit ? ` ${unit}` : ''}`;
}
function operatorLabel(operator?: AlarmOperator) { return [...numericOperatorOptions, ...textOperatorOptions].find((item) => item.value === (operator || 'GT'))?.label || operator || '--'; }
function ruleScopeCell(row: AlarmRuleVo) { const device = row.deviceId ? `${row.deviceName || row.deviceCode || row.deviceId}${row.deviceName && row.deviceCode ? `（${row.deviceCode}）` : ''}` : '全部设备';
  const variantLocationCount = new Set(row.variants.map((variant) => variant.locationId).filter(Boolean)).size;
  const location = row.locationId ? row.locationPath || row.locationName || row.locationId : variantLocationCount ? `${variantLocationCount} 个位置（按变体隔离）` : '全部位置';
  return `${device} · ${location}`; }
function ruleActions(row: AlarmRuleVo) { if (!canManage.value) return '--';
  if (row.managedBy !== 'MANUAL') return h('span', { class: 'rule-management-hint' }, '请在来源业务管理');
  if (row.runtimeStatus === 'ARCHIVED') return '--';
  return h(NSpace, { size: 8 }, { default: () => [
    h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => openRule(row) }, { default: () => '编辑' }),
    h(NButton, { text: true, size: 'small', onClick: () => toggleRuntimeStatus(row) }, { default: () => row.runtimeStatus === 'ACTIVE' ? '停用' : '启用' }),
    h(NButton, { text: true, type: 'error', size: 'small', onClick: () => confirmArchive(row) }, { default: () => '归档' }),
  ] }); }
function deviceLabel(device: DeviceVo) { const name = device.name || device.code || device.id || '--'; const code = device.code && device.code !== name ? `（${device.code}）` : '';
  return `${name}${code}${device.locationPath ? ` · ${device.locationPath}` : ''}`; }
function locationLabel(location: MuseumLocationManageVo) { const name = location.path || location.name || location.id || '--'; return `${name}${location.code ? `（${location.code}）` : ''}`; }
function filterScopeOption(pattern: string, option: SelectOption & { searchText?: string }) { const label = typeof option.label === 'string' ? option.label : '';
  return `${option.searchText || ''} ${label}`.toLowerCase().includes(pattern.trim().toLowerCase()); }
function metricSupportsRule(metric: StandardMetricDefinition, type: AlarmRuleType): boolean { if (type === 'VALUE_CONDITION') return metric.valueType === 'NUMBER';
  if (type === 'STATE_CONDITION') return ['STRING', 'BOOLEAN'].includes(metric.valueType); return type === 'DATA_QUALITY'; }
function usesSecondThreshold(condition: AlarmRuleConditionVo) { return usesNumericCondition.value && ['BETWEEN', 'OUTSIDE'].includes(condition.operator || ''); }
function normalizeCondition(condition: AlarmRuleConditionVo) { if (!usesSecondThreshold(condition)) condition.thresholdValue2 = null; }
function syncConditions() { editableBranches.value.forEach((branch) => branch.conditions.forEach((condition) => {
  condition.recordType = ruleForm.value.recordType || undefined; condition.recordKey = ruleForm.value.recordKey;
  if (ruleForm.value.ruleType === 'VALUE_CONDITION') {
    const fields = selectedStandardMetric.value?.valueFields || [];
    if (!fields.some((field) => field.code === condition.fieldCode)) condition.fieldCode = fields.length === 1 ? fields[0].code : undefined;
  } else condition.fieldCode = undefined;
})); }
function onRuleTypeUpdate(type: AlarmRuleType) { ruleForm.value.ruleType = type;
  if (type === 'DEVICE_OFFLINE') { ruleForm.value.recordKey = 'DEVICE_OFFLINE'; ruleForm.value.recordType = 'DEVICE_HEALTH'; }
  else { ruleForm.value.recordKey = undefined; ruleForm.value.recordType = null; }
  editableBranches.value.forEach((branch) => { branch.conditions = [createCondition(type)]; }); syncConditions(); }
function onRecordKeyUpdate(value: string | null) { ruleForm.value.recordKey = value || undefined;
  ruleForm.value.recordType = standardMetrics.value.find((item) => item.code === value)?.recordType || null;
  editableBranches.value.forEach((branch) => branch.conditions.forEach((condition) => { condition.fieldCode = undefined; }));
  syncConditions(); }
function addBranch() { editableBranches.value.push(createBranch(ruleForm.value.ruleType)); syncConditions(); }
function removeBranch(index: number) { editableBranches.value.splice(index, 1); }
function addCondition(branchIndex: number) { editableBranches.value[branchIndex].conditions.push(createCondition(ruleForm.value.ruleType)); syncConditions(); }
function openRule(row?: AlarmRuleVo) { branchSequence = 0; ruleForm.value = row ? cloneDeep(row) : createDefaultRule();
  if (!ruleForm.value.variants.length) ruleForm.value.variants = [{ code: 'GENERAL', name: '通用', branches: [createBranch(ruleForm.value.ruleType)] }];
  scopeDevices.value = []; scopeLocations.value = []; if (ruleForm.value.orgId) void loadScopeCatalog(ruleForm.value.orgId); ruleShow.value = true; }
function onRuleOrgUpdate(orgId: string | null) { ruleForm.value.orgId = orgId || undefined; ruleForm.value.deviceId = null; ruleForm.value.locationId = null;
  scopeDevices.value = []; scopeLocations.value = []; if (orgId) void loadScopeCatalog(orgId); }
async function loadScopeCatalog(orgId: string) { const requestId = ++scopeCatalogRequestId; scopeCatalogLoading.value = true;
  try { const [deviceResponse, locationResponse]: any[] = await Promise.all([ingestApi.devices(undefined, orgId), ingestApi.locationCatalog({ orgId })]);
    if (requestId !== scopeCatalogRequestId || ruleForm.value.orgId !== orgId) return;
    scopeDevices.value = responseList<DeviceVo>(deviceResponse); scopeLocations.value = responseList<MuseumLocationManageVo>(locationResponse);
  } catch { if (requestId !== scopeCatalogRequestId) return; scopeDevices.value = []; scopeLocations.value = []; message.error('设备和位置选项加载失败，请稍后重试'); }
  finally { if (requestId === scopeCatalogRequestId) scopeCatalogLoading.value = false; } }
function responseList<T>(response: any): T[] { const data = response?.data; return Array.isArray(data) ? data : Array.isArray(data?.list) ? data.list : []; }
function validateRule(): boolean {
  if (!ruleForm.value.orgId) return message.warning('请选择所属场馆'), false;
  if (!ruleForm.value.code?.trim() || !ruleForm.value.name?.trim() || !ruleForm.value.recordKey?.trim() || !ruleForm.value.recordType)
    return message.warning('请填写规则编码、名称和标准事实'), false;
  if (!editableBranches.value.length) return message.warning('至少添加一个条件分支'), false;
  for (const branch of editableBranches.value) {
    if (!branch.code?.trim() || !branch.name?.trim() || !branch.severity || !branch.conditions.length) return message.warning('请完整填写所有分支'), false;
    for (const condition of branch.conditions) {
      if (!condition.operator) return message.warning('条件运算符必填'), false;
      if (ruleForm.value.ruleType === 'VALUE_CONDITION' && !condition.fieldCode) return message.warning('数值条件必须选择指标字段'), false;
      if (usesExpectedValue.value && !condition.expectedValue?.trim()) return message.warning('期望/异常值必填'), false;
      if (usesNumericCondition.value && condition.thresholdValue == null) return message.warning('数值阈值必填'), false;
      if (usesSecondThreshold(condition) && condition.thresholdValue2 == null) return message.warning('区间条件的第二阈值必填'), false;
    }
  }
  return true;
}
async function saveRule() { syncConditions(); if (!validateRule()) return; ruleSaving.value = true;
  try { const payload = cloneDeep(ruleForm.value); payload.managedBy = 'MANUAL'; payload.variants = [{ ...payload.variants[0], code: 'GENERAL', name: '通用', priority: 0 }];
    await ingestApi.saveAlarmRule(payload); ruleShow.value = false; message.success('已发布新的不可变规则版本'); ruleTableRef.value?.handleRefresh?.(); }
  finally { ruleSaving.value = false; } }
async function toggleRuntimeStatus(row: AlarmRuleVo) { if (!row.id) return; const status = row.runtimeStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
  await ingestApi.setAlarmRuleRuntimeStatus(row.id, status); message.success(status === 'ACTIVE' ? '规则已启用' : '规则已停用'); ruleTableRef.value?.handleRefresh?.(); }
function confirmArchive(row: AlarmRuleVo) { if (!row.id) return; dialog.warning({ title: '归档规则',
  content: `归档“${row.name || row.code}”会关闭其当前活动告警，历史版本与转换记录仍保留。`, positiveText: '归档', negativeText: '取消',
  onPositiveClick: async () => { await ingestApi.deleteAlarmRule(row.id!); message.success('规则已归档'); ruleTableRef.value?.handleRefresh?.(); } }); }
async function loadStandardMetrics() { try { const response: any = await ingestApi.standardMetrics(true); standardMetrics.value = responseList<StandardMetricDefinition>(response); }
  catch { standardMetrics.value = []; message.error('标准指标加载失败，请稍后重试'); } }
onMounted(loadStandardMetrics);
</script>

<style scoped>
.alarm-page, .alarm-tabs { display: flex; width: 100%; height: 100%; min-height: 0; flex: 1; flex-direction: column; overflow: hidden; }
.alarm-tabs :deep(.n-tabs-nav) { flex: none; }
.alarm-tabs :deep(.n-tabs-pane-wrapper), .alarm-tabs :deep(.n-tab-pane) { display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: hidden; }
.alarm-data-table { width: 100%; height: 100%; min-height: 0; flex: 1; }
.branch-card { margin-bottom: 12px; }
.condition-row + .condition-row { border-top: 1px dashed var(--n-border-color); padding-top: 8px; }
.rule-management-hint { font-size: 12px; opacity: 0.62; white-space: nowrap; }
</style>
