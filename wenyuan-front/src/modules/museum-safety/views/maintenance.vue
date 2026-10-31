<template>
  <SafetyPageGuard>
    <div class="maintenance-page">
      <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
      <NTabs
        v-model:value="activeTab"
        type="line"
        animated
        class="safety-tabs"
        pane-wrapper-style="display: flex; flex: 1; min-height: 0; overflow: hidden;"
        pane-style="display: flex; flex: 1; min-height: 0; flex-direction: column; overflow: hidden;"
      >
        <NTabPane name="plans" tab="维护计划">
          <HPageDataTable
            ref="planTableRef"
            title="维护计划"
            class="safety-page-table"
            :columns="planColumns"
            :fetch-data="safetyApi.maintenancePlanPage"
            :params="tableQuery"
            :scroll-x="1200"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm>
              <NFormItem label="所属场馆">
                <VenueSelect
                  v-model:value="orgId"
                  clearable
                  placeholder="全部场馆"
                  style="width: 240px"
                  @update:value="venueChanged"
                />
              </NFormItem>
            </template>
            <template #toolbar-extra>
              <NButton v-if="canMaintain" type="primary" @click="openPlan">新增计划</NButton>
            </template>
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="tasks" tab="维护任务">
          <HPageDataTable
            ref="taskTableRef"
            title="维护任务"
            class="safety-page-table"
            :columns="taskColumns"
            :fetch-data="safetyApi.maintenanceTaskPage"
            :params="taskQuery"
            :scroll-x="1180"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm>
              <NFormItem label="所属场馆">
                <VenueSelect
                  v-model:value="orgId"
                  clearable
                  placeholder="全部场馆"
                  style="width: 240px"
                  @update:value="venueChanged"
                />
              </NFormItem>
              <NFormItem label="任务状态">
                <NSelect
                  v-model:value="statusFilter"
                  :options="statusOptions"
                  clearable
                  placeholder="全部状态"
                  style="width: 150px"
                />
              </NFormItem>
            </template>
            <template #toolbar-extra>
              <NButton v-if="canMaintain" type="primary" @click="openTask()">创建任务</NButton>
            </template>
          </HPageDataTable>
        </NTabPane>
        <NTabPane name="records" tab="维护记录">
          <HPageDataTable
            ref="recordTableRef"
            title="维护记录"
            class="safety-page-table"
            :columns="recordColumns"
            :fetch-data="safetyApi.maintenanceRecordPage"
            :params="tableQuery"
            :scroll-x="1280"
            row-key-field="id"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
          >
            <template #queryForm>
              <NFormItem label="所属场馆">
                <VenueSelect
                  v-model:value="orgId"
                  clearable
                  placeholder="全部场馆"
                  style="width: 240px"
                  @update:value="venueChanged"
                />
              </NFormItem>
            </template>
            <template #toolbar-extra><NButton @click="exportRecords">导出维护台账</NButton></template>
          </HPageDataTable>
        </NTabPane>
      </NTabs>

      <HPageForm
        v-model:show="planVisible"
        title="新增维护计划"
        :model="planForm"
        :auto-close="false"
        :handle-submit="savePlan"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect v-model:value="planForm.orgId" placeholder="请选择场馆" @update:value="planVenueChanged"
        /></NFormItem>
        <NFormItem label="计划编码"><NInput value="保存后自动生成" disabled /></NFormItem
        ><NFormItem label="计划名称" required><NInput v-model:value="planForm.name" /></NFormItem>
        <NFormItem label="安全领域"
          ><NSelect
            v-model:value="planForm.domain"
            :options="domainOptions"
            @update:value="planDomainChanged" /></NFormItem
        ><NFormItem label="设备类型" required
          ><NSelect
            v-model:value="planForm.equipmentType"
            :options="equipmentTypeOptions"
            filterable
            placeholder="从安全防护设备目录选择"
        /></NFormItem>
        <NFormItem label="指定设备"
          ><NSelect
            v-model:value="planForm.deviceId"
            :options="deviceOptions"
            clearable
            filterable
            @update:value="planDeviceChanged" /></NFormItem
        ><NFormItem label="维护类型" required
          ><HDictSelect
            v-model:value="planForm.maintenanceType"
            type="museum_safety_maintenance_type"
            :fallback-options="maintenanceTypeOptions"
            placeholder="请选择维护类型"
        /></NFormItem>
        <NFormItem label="维护周期"
          ><NInputNumber v-model:value="planForm.cycleDays" :min="1" :max="3650" /><span class="unit"
            >天</span
          ></NFormItem
        ><NFormItem label="下次维护"><NDatePicker v-model:value="planForm.nextDueAt" type="datetime" /></NFormItem>
        <NFormItem label="责任人" required
          ><UserSelect v-model:value="planForm.responsibleUserId" clearable placeholder="请选择责任人" /></NFormItem
        ><NFormItem label="预计工时"
          ><NInputNumber v-model:value="planForm.expectedMinutes" :min="1" clearable /><span class="unit"
            >分钟</span
          ></NFormItem
        >
        <NFormItem label="检测机构"><NInput v-model:value="planForm.professionalOrganization" /></NFormItem
        ><NFormItem label="气象预警关联"><NSwitch v-model:value="planForm.weatherWarningLinked" /></NFormItem>
        <NFormItem label="标准依据"
          ><NInput v-model:value="basisText" placeholder="编码|名称|条款，多个用分号分隔"
        /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="taskVisible"
        title="创建维护任务"
        :model="taskForm"
        :auto-close="false"
        :handle-submit="saveTask"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect v-model:value="taskForm.orgId" placeholder="请选择场馆" @update:value="taskVenueChanged"
        /></NFormItem>
        <NFormItem label="维护计划"
          ><NSelect
            v-model:value="taskForm.planId"
            :options="planOptions"
            clearable
            filterable
            @update:value="taskPlanChanged" /></NFormItem
        ><NFormItem label="关联异常事项"
          ><NSelect
            v-model:value="taskForm.issueId"
            :options="issueOptions"
            clearable
            filterable
            placeholder="选择待处置的巡检异常"
            @update:value="taskIssueChanged"
        /></NFormItem>
        <NFormItem label="设备" required
          ><NSelect v-model:value="taskForm.deviceId" :options="taskDeviceOptions" filterable /></NFormItem
        ><NFormItem label="维护人员" required
          ><UserSelect v-model:value="taskForm.assigneeId" clearable placeholder="请选择维护人员"
        /></NFormItem>
        <NFormItem label="计划时间"><NDatePicker v-model:value="taskForm.scheduledAt" type="datetime" /></NFormItem
        ><NFormItem label="维护要求" required
          ><NInput v-model:value="taskForm.instructions" type="textarea"
        /></NFormItem>
      </HPageForm>

      <HPageForm
        v-model:show="completeVisible"
        title="完成维护任务"
        :model="completeForm"
        :auto-close="false"
        :handle-submit="completeTask"
        label-placement="left"
        label-width="110"
        submit-text="提交"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="完成时间"><NDatePicker v-model:value="completeForm.completedAt" type="datetime" /></NFormItem
        ><NFormItem label="实际工时"
          ><NInputNumber v-model:value="completeForm.actualMinutes" :min="1" /><span class="unit">分钟</span></NFormItem
        >
        <NFormItem label="维护内容" required><NInput v-model:value="completeForm.content" type="textarea" /></NFormItem
        ><NFormItem label="检测数据"
          ><NInput v-model:value="measurementsText" placeholder='JSON，如：{"groundResistance":0.8}'
        /></NFormItem>
        <NFormItem label="标准限值"
          ><NInput v-model:value="limitsText" placeholder='JSON，如：{"groundResistance":1.0}' /></NFormItem
        ><NFormItem label="校准结果"><NInput v-model:value="completeForm.calibrationResult" /></NFormItem>
        <NFormItem label="更换零件"
          ><NInput
            v-model:value="partsText"
            type="textarea"
            placeholder='JSON 数组，如：[{"model":"SPD-40KA","quantity":2}]'
        /></NFormItem>
        <NFormItem label="零件费用"
          ><NInputNumber v-model:value="partsCostYuan" :min="0" :precision="2" /><span class="unit">元</span></NFormItem
        ><NFormItem label="人工费用"
          ><NInputNumber v-model:value="laborCostYuan" :min="0" :precision="2" /><span class="unit">元</span></NFormItem
        >
        <NFormItem label="检测机构"><NInput v-model:value="completeForm.testingOrganization" /></NFormItem
        ><NFormItem label="检测仪器编号"><NInput v-model:value="completeForm.instrumentNo" /></NFormItem
        ><NFormItem label="防雷效果评估"
          ><NInput v-model:value="completeForm.effectAssessment" type="textarea"
        /></NFormItem>
        <NFormItem label="证书编号"><NInput v-model:value="completeForm.certificateNo" /></NFormItem
        ><NFormItem label="是否合格"><NSwitch v-model:value="completeForm.compliant" /></NFormItem
        ><NFormItem label="整改建议"><NInput v-model:value="completeForm.recommendation" type="textarea" /></NFormItem>
        <NFormItem label="过程影像"
          ><SafetyAttachmentUpload v-model="completeForm.attachments" accept="image/*,video/*"
        /></NFormItem>
      </HPageForm>
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue';
import * as XLSX from 'xlsx';
import {
  NAlert,
  NButton,
  NDatePicker,
  NFormItem,
  NInput,
  NInputNumber,
  NSelect,
  NSpace,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  type DataTableColumns,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import HDictSelect from '@/components/form/field/dict/select.vue';
import { ingestApi } from '../../museum-device/api/ingest';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import type { RecordFilterOptionsVo } from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type {
  SafetyComplianceBasis,
  SafetyDeviceProfileVo,
  SafetyDomain,
  SafetyInspectionIssueVo,
  SafetyInspectionRecordVo,
  SafetyMaintenancePlanVo,
  SafetyMaintenanceRecordVo,
  SafetyMaintenanceStatus,
  SafetyMaintenanceTaskVo,
} from '../typings';
import SafetyAttachmentUpload from '../components/SafetyAttachmentUpload.vue';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';

const message = useMessage();
const authStore = useAuthStore();
const canMaintain = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_MAINTENANCE'));
const activeTab = ref('plans');
const orgId = ref<string | null>(null);
const statusFilter = ref<SafetyMaintenanceStatus | null>(null);
const loading = ref(false);
const error = ref('');
const planTableRef = ref<any>(null);
const taskTableRef = ref<any>(null);
const recordTableRef = ref<any>(null);
const plans = ref<SafetyMaintenancePlanVo[]>([]);
const tasks = ref<SafetyMaintenanceTaskVo[]>([]);
const records = ref<SafetyMaintenanceRecordVo[]>([]);
const inspectionIssues = ref<SafetyInspectionIssueVo[]>([]);
const inspectionRecords = ref<SafetyInspectionRecordVo[]>([]);
const catalog = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const deviceProfiles = ref<SafetyDeviceProfileVo[]>([]);
const selectedTask = ref<SafetyMaintenanceTaskVo | null>(null);
const planVisible = ref(false);
const taskVisible = ref(false);
const completeVisible = ref(false);
const tableQuery = computed(() => ({ orgId: orgId.value || undefined }));
const taskQuery = computed(() => ({
  orgId: orgId.value || undefined,
  status: statusFilter.value || undefined,
}));
const domainOptions = [
  { label: '建筑结构', value: 'BUILDING' },
  { label: '防雷', value: 'LIGHTNING' },
  { label: '消防', value: 'FIRE' },
  { label: '安防', value: 'SECURITY' },
  { label: '用电', value: 'ELECTRICITY' },
  { label: '建筑环境', value: 'BUILDING_ENVIRONMENT' },
];
const statusOptions = [
  { label: '待安排', value: 'PENDING' },
  { label: '已指派', value: 'ASSIGNED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已复核', value: 'VERIFIED' },
  { label: '已取消', value: 'CANCELLED' },
];
const maintenanceTypeOptions = [
  { label: '日常保养', value: '日常保养' },
  { label: '定期检修', value: '定期检修' },
  { label: '故障维修', value: '故障维修' },
  { label: '年度检测', value: '年度检测' },
  { label: '接地电阻测试', value: '接地电阻测试' },
  { label: '浪涌保护器更换', value: '浪涌保护器更换' },
];
const deviceOptions = computed(() =>
  catalog.value.devices
    .filter((item) => deviceProfiles.value.some((profile) => profile.deviceId === item.id))
    .map((item) => ({
      label: `${item.name}（${item.code}） · ${item.locationPath || '未配置位置'}`,
      value: item.id,
    })),
);
const equipmentTypeOptions = computed(() =>
  [
    ...new Set(
      deviceProfiles.value
        .filter((item) => item.orgId === planForm.orgId && item.domain === planForm.domain)
        .map((item) => item.equipmentType),
    ),
  ].map((value) => ({ label: value, value })),
);
const taskDeviceOptions = computed(() => {
  const plan = plans.value.find((item) => item.id === taskForm.planId);
  if (!plan) return deviceOptions.value;
  const allowedIds = new Set(
    deviceProfiles.value
      .filter((profile) => profile.equipmentType === plan.equipmentType && profile.domain === plan.domain)
      .map((profile) => profile.deviceId),
  );
  return deviceOptions.value.filter((item) => allowedIds.has(item.value));
});
const planOptions = computed(() =>
  plans.value
    .filter((item) => !taskForm.orgId || item.orgId === taskForm.orgId)
    .map((item) => ({ label: `${item.code} · ${item.name}`, value: item.id })),
);
const issueOptions = computed(() =>
  inspectionIssues.value
    .filter(
      (item) =>
        item.orgId === taskForm.orgId && !item.maintenanceTaskId && !['VERIFIED', 'CANCELLED'].includes(item.status),
    )
    .filter((item) => {
      const deviceId = inspectionRecords.value.find((record) => record.id === item.inspectionRecordId)?.deviceId;
      return !deviceId || !taskForm.deviceId || deviceId === taskForm.deviceId;
    })
    .map((item) => ({ label: `${item.issueType} · ${item.description}`, value: item.id })),
);
const planForm = reactive({
  orgId: null as string | null,
  code: '',
  name: '',
  domain: 'SECURITY' as SafetyDomain,
  equipmentType: '',
  deviceId: null as string | null,
  maintenanceType: '',
  cycleDays: 90,
  nextDueAt: Date.now(),
  responsibleUserId: '',
  expectedMinutes: null as number | null,
  professionalOrganization: '',
  weatherWarningLinked: false,
});
const basisText = ref('GB/T 16571-2012|博物馆和文物保护单位安全防范系统要求');
const taskForm = reactive({
  orgId: null as string | null,
  planId: null as string | null,
  issueId: '',
  deviceId: '',
  assigneeId: '',
  scheduledAt: Date.now(),
  instructions: '',
});
const completeForm = reactive({
  completedAt: Date.now(),
  actualMinutes: 60,
  content: '',
  calibrationResult: '',
  testingOrganization: '',
  instrumentNo: '',
  certificateNo: '',
  compliant: true,
  recommendation: '',
  effectAssessment: '',
  attachments: [] as string[],
});
const measurementsText = ref('{}');
const limitsText = ref('{}');
const partsText = ref('[]');
const partsCostYuan = ref(0);
const laborCostYuan = ref(0);
const planColumns: DataTableColumns<SafetyMaintenancePlanVo> = [
  { title: '编码', key: 'code', width: 130 },
  { title: '计划', key: 'name', minWidth: 180 },
  { title: '领域', key: 'domain', width: 110 },
  { title: '设备类型', key: 'equipmentType', minWidth: 150 },
  { title: '维护类型', key: 'maintenanceType', width: 150 },
  { title: '周期', key: 'cycleDays', width: 90, render: (r) => `${r.cycleDays} 天` },
  {
    title: '下次到期',
    key: 'nextDueAt',
    width: 180,
    render: (r) => h('span', { class: r.nextDueAt < Date.now() ? 'overdue' : '' }, formatDateTime(r.nextDueAt)),
  },
  { title: '责任人', key: 'responsibleUserId', width: 130 },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    fixed: 'right',
    render: (r) =>
      canMaintain.value
        ? h(NButton, { text: true, type: 'primary', onClick: () => openTask(r) }, { default: () => '创建任务' })
        : '--',
  },
];
const taskColumns: DataTableColumns<SafetyMaintenanceTaskVo> = [
  { title: '计划时间', key: 'scheduledAt', width: 180, render: (r) => formatDateTime(r.scheduledAt) },
  {
    title: '设备',
    key: 'deviceId',
    minWidth: 180,
    render: (r) => deviceOptions.value.find((i) => i.value === r.deviceId)?.label || r.deviceId,
  },
  { title: '维护人员', key: 'assigneeId', width: 130 },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (r) =>
      h(
        NTag,
        {
          type:
            r.status === 'COMPLETED' || r.status === 'VERIFIED'
              ? 'success'
              : r.status === 'CANCELLED'
                ? 'default'
                : 'warning',
          bordered: false,
        },
        { default: () => statusOptions.find((i) => i.value === r.status)?.label || r.status },
      ),
  },
  { title: '维护要求', key: 'instructions', minWidth: 240, ellipsis: { tooltip: true } },
  {
    title: '开始时间',
    key: 'startedAt',
    width: 180,
    render: (r) => (r.startedAt ? formatDateTime(r.startedAt) : '--'),
  },
  {
    title: '完成时间',
    key: 'completedAt',
    width: 180,
    render: (r) => (r.completedAt ? formatDateTime(r.completedAt) : '--'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    fixed: 'right',
    render: (r) =>
      h(NSpace, null, {
        default: () => [
          canMaintain.value && r.status === 'ASSIGNED'
            ? h(NButton, { text: true, type: 'primary', onClick: () => startTask(r) }, { default: () => '开始' })
            : null,
          canMaintain.value && ['ASSIGNED', 'PROCESSING'].includes(r.status)
            ? h(NButton, { text: true, type: 'success', onClick: () => openComplete(r) }, { default: () => '完成' })
            : null,
        ],
      }),
  },
];
const recordColumns: DataTableColumns<SafetyMaintenanceRecordVo> = [
  { title: '完成时间', key: 'completedAt', width: 180, render: (r) => formatDateTime(r.completedAt) },
  {
    title: '设备',
    key: 'deviceId',
    minWidth: 200,
    render: (r) => deviceOptions.value.find((item) => item.value === r.deviceId)?.label || r.deviceId,
  },
  { title: '维护人员', key: 'assigneeId', width: 130 },
  { title: '维护内容', key: 'content', minWidth: 220, render: (r) => String(r.snapshot.content || '--') },
  {
    title: '合规结论',
    key: 'compliant',
    width: 110,
    render: (r) =>
      h(
        NTag,
        { type: r.snapshot.compliant === true ? 'success' : 'error', bordered: false },
        { default: () => (r.snapshot.compliant === true ? '合格' : '不合格') },
      ),
  },
  { title: '证书编号', key: 'certificateNo', width: 150, render: (r) => String(r.snapshot.certificateNo || '--') },
  {
    title: '检测数据',
    key: 'measurements',
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (r) => JSON.stringify(r.snapshot.measurements || {}),
  },
];
function parseBasis(): SafetyComplianceBasis[] {
  return basisText.value
    .split(/[;；]/)
    .map((v) => v.trim())
    .filter(Boolean)
    .map((item) => {
      const [code, title = code, clause] = item.split('|').map((v) => v.trim());
      return { code, title, clause: clause || null };
    });
}
function parseObject(value: string, label: string) {
  try {
    const result = JSON.parse(value || '{}');
    if (!result || Array.isArray(result) || typeof result !== 'object') throw new Error();
    return result as Record<string, unknown>;
  } catch {
    throw new Error(`${label}必须是有效 JSON 对象。`);
  }
}
function parseArray(value: string, label: string): Array<Record<string, unknown>> {
  try {
    const result = JSON.parse(value || '[]');
    if (!Array.isArray(result) || result.some((item) => !item || Array.isArray(item) || typeof item !== 'object')) {
      throw new Error();
    }
    return result;
  } catch {
    throw new Error(`${label}必须是 JSON 对象数组。`);
  }
}
function calculatedCompliance(measurements: Record<string, unknown>, limits: Record<string, unknown>): boolean | null {
  const comparisons = Object.entries(limits).flatMap(([limitKey, rawLimit]) => {
    const range = rawLimit && typeof rawLimit === 'object' && !Array.isArray(rawLimit) ? rawLimit : null;
    const minimum = Number((range as Record<string, unknown> | null)?.min);
    const maximum = Number((range as Record<string, unknown> | null)?.max);
    const direct = Number(rawLimit);
    const normalizedKey = limitKey.replace(/(最大值|最小值|上限|下限)$/u, '').replace(/^(max:|min:)/u, '');
    const actual = Number(measurements[limitKey] ?? measurements[normalizedKey]);
    if (!Number.isFinite(actual)) return [];
    const lower = Number.isFinite(minimum)
      ? minimum
      : /^(min:)/u.test(limitKey) || /(最小值|下限)$/u.test(limitKey)
        ? direct
        : Number.NaN;
    const upper = Number.isFinite(maximum) ? maximum : !Number.isFinite(lower) ? direct : Number.NaN;
    if (!Number.isFinite(lower) && !Number.isFinite(upper)) return [];
    return [(!Number.isFinite(lower) || actual >= lower) && (!Number.isFinite(upper) || actual <= upper)];
  });
  return comparisons.length ? comparisons.every(Boolean) : null;
}
async function loadCatalog(value: string | null = orgId.value) {
  try {
    if (value) {
      const [options, profiles] = await Promise.all([
        ingestApi.recordFilterOptions({ orgId: value }),
        safetyApi.safetyDeviceProfiles({ orgId: value }),
      ]);
      catalog.value = unwrapResponse<RecordFilterOptionsVo>(options);
      deviceProfiles.value = unwrapResponse<SafetyDeviceProfileVo[]>(profiles) || [];
    } else {
      catalog.value = { devices: [], locations: [] };
      deviceProfiles.value = [];
    }
  } catch {
    catalog.value = { devices: [], locations: [] };
    deviceProfiles.value = [];
  }
}
function planDeviceChanged(value: string | null) {
  const profile = deviceProfiles.value.find((item) => item.deviceId === value);
  if (profile) {
    planForm.equipmentType = profile.equipmentType;
    planForm.domain = profile.domain;
  }
}
function planVenueChanged() {
  planForm.deviceId = null;
  planForm.equipmentType = '';
  void loadCatalog(planForm.orgId);
}
function planDomainChanged() {
  planForm.deviceId = null;
  planForm.equipmentType = '';
}
async function venueChanged() {
  await reload();
}
function refreshTables() {
  planTableRef.value?.handleRefresh?.();
  taskTableRef.value?.handleRefresh?.();
  recordTableRef.value?.handleRefresh?.();
}
async function reload() {
  loading.value = true;
  error.value = '';
  try {
    const [p, t, r, i, inspections] = await Promise.all([
      safetyApi.maintenancePlans({ orgId: orgId.value || undefined }),
      safetyApi.maintenanceTasks({ orgId: orgId.value || undefined, status: statusFilter.value || undefined }),
      safetyApi.maintenanceRecords({ orgId: orgId.value || undefined }),
      safetyApi.inspectionIssues({ orgId: orgId.value || undefined }),
      safetyApi.inspectionRecords({ orgId: orgId.value || undefined }),
    ]);
    plans.value = unwrapResponse<SafetyMaintenancePlanVo[]>(p) || [];
    tasks.value = unwrapResponse<SafetyMaintenanceTaskVo[]>(t) || [];
    records.value = unwrapResponse<SafetyMaintenanceRecordVo[]>(r) || [];
    inspectionIssues.value = unwrapResponse<SafetyInspectionIssueVo[]>(i) || [];
    inspectionRecords.value = unwrapResponse<SafetyInspectionRecordVo[]>(inspections) || [];
    await loadCatalog();
    refreshTables();
  } catch (cause: any) {
    error.value = cause?.message || '设备维护数据加载失败。';
  } finally {
    loading.value = false;
  }
}
function exportRecords() {
  const values = records.value.map((row) => ({
    记录ID: row.id,
    任务ID: row.taskId,
    设备ID: row.deviceId,
    维护人员: row.assigneeId,
    完成时间: formatDateTime(row.completedAt),
    维护内容: row.snapshot.content || '',
    检测数据: JSON.stringify(row.snapshot.measurements || {}),
    标准限值: JSON.stringify(row.snapshot.standardLimits || {}),
    校准结果: row.snapshot.calibrationResult || '',
    零件信息: JSON.stringify(row.snapshot.parts || []),
    零件费用分: row.snapshot.partsCostCents || 0,
    人工费用分: row.snapshot.laborCostCents || 0,
    检测机构: row.snapshot.testingOrganization || '',
    检测仪器编号: row.snapshot.instrumentNo || '',
    证书编号: row.snapshot.certificateNo || '',
    是否合格: row.snapshot.compliant === true ? '是' : '否',
    自动比对结论:
      row.snapshot.calculatedCompliant === true ? '合格' : row.snapshot.calculatedCompliant === false ? '不合格' : '',
    标准比对明细: JSON.stringify(row.snapshot.standardComparison || []),
    整改建议: row.snapshot.recommendation || '',
    防雷效果评估: row.snapshot.effectAssessment || '',
    计划依据: JSON.stringify((row.snapshot.plan as Record<string, unknown> | null)?.complianceBasis || []),
  }));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(values), '维护记录');
  XLSX.writeFile(workbook, '设备维护台账.xlsx');
}
async function loadTasks() {
  try {
    tasks.value =
      unwrapResponse<SafetyMaintenanceTaskVo[]>(
        await safetyApi.maintenanceTasks({ orgId: orgId.value || undefined, status: statusFilter.value || undefined }),
      ) || [];
    taskTableRef.value?.handleRefresh?.();
  } catch (cause: any) {
    error.value = cause?.message || '维护任务加载失败。';
  }
}
function openPlan() {
  Object.assign(planForm, {
    orgId: orgId.value,
    code: '',
    name: '',
    domain: 'SECURITY',
    equipmentType: '',
    deviceId: null,
    maintenanceType: '',
    cycleDays: 90,
    nextDueAt: Date.now(),
    responsibleUserId: '',
    expectedMinutes: null,
    professionalOrganization: '',
    weatherWarningLinked: false,
  });
  basisText.value = 'GB/T 16571-2012|博物馆和文物保护单位安全防范系统要求';
  planVisible.value = true;
}
function openTask(plan?: SafetyMaintenancePlanVo) {
  Object.assign(taskForm, {
    orgId: plan?.orgId || orgId.value,
    planId: plan?.id || null,
    issueId: '',
    deviceId: plan?.deviceId || '',
    assigneeId: plan?.responsibleUserId || '',
    scheduledAt: plan?.nextDueAt || Date.now(),
    instructions: plan ? `${plan.maintenanceType}：${plan.name}` : '',
  });
  void loadCatalog(taskForm.orgId);
  void loadInspectionIssues(taskForm.orgId);
  taskVisible.value = true;
}
async function loadInspectionIssues(orgId: string | null) {
  if (!orgId) {
    inspectionIssues.value = [];
    inspectionRecords.value = [];
    return;
  }
  try {
    const [issues, inspections] = await Promise.all([
      safetyApi.inspectionIssues({ orgId }),
      safetyApi.inspectionRecords({ orgId }),
    ]);
    inspectionIssues.value = unwrapResponse<SafetyInspectionIssueVo[]>(issues) || [];
    inspectionRecords.value = unwrapResponse<SafetyInspectionRecordVo[]>(inspections) || [];
  } catch (cause: any) {
    error.value = cause?.message || '关联异常事项加载失败。';
  }
}
function taskVenueChanged() {
  taskForm.planId = null;
  taskForm.issueId = '';
  taskForm.deviceId = '';
  void loadCatalog(taskForm.orgId);
  void loadInspectionIssues(taskForm.orgId);
}
function taskIssueChanged(value: string | null) {
  const issue = inspectionIssues.value.find((item) => item.id === value);
  const deviceId = inspectionRecords.value.find((record) => record.id === issue?.inspectionRecordId)?.deviceId;
  if (deviceId) taskForm.deviceId = deviceId;
}
function taskPlanChanged(value: string | null) {
  const plan = plans.value.find((item) => item.id === value);
  if (!plan) return;
  taskForm.issueId = '';
  taskForm.orgId = plan.orgId;
  taskForm.deviceId = plan.deviceId || '';
  taskForm.assigneeId = plan.responsibleUserId;
  taskForm.scheduledAt = plan.nextDueAt;
  taskForm.instructions = `${plan.maintenanceType}：${plan.name}`;
  void loadCatalog(plan.orgId);
  void loadInspectionIssues(plan.orgId);
}
function openComplete(row: SafetyMaintenanceTaskVo) {
  selectedTask.value = row;
  Object.assign(completeForm, {
    completedAt: Date.now(),
    actualMinutes: 60,
    content: '',
    calibrationResult: '',
    testingOrganization: '',
    instrumentNo: '',
    certificateNo: '',
    compliant: true,
    recommendation: '',
    effectAssessment: '',
    attachments: [],
  });
  measurementsText.value = '{}';
  limitsText.value = '{}';
  partsText.value = '[]';
  partsCostYuan.value = 0;
  laborCostYuan.value = 0;
  completeVisible.value = true;
}
async function savePlan() {
  if (
    !planForm.orgId ||
    !planForm.name ||
    !planForm.equipmentType ||
    !planForm.maintenanceType ||
    !planForm.responsibleUserId
  ) {
    error.value = '请完整填写场馆、计划、设备类型、维护类型和责任人。';
    return;
  }
  try {
    await safetyApi.createMaintenancePlan({ ...planForm, complianceBasis: parseBasis() });
    message.success('维护计划创建成功');
    planVisible.value = false;
    await reload();
  } catch (cause: any) {
    error.value = cause?.message || '维护计划创建失败。';
  }
}
async function saveTask() {
  if (!taskForm.orgId || !taskForm.deviceId || !taskForm.assigneeId || !taskForm.instructions) {
    error.value = '请完整填写场馆、设备、维护人员和要求。';
    return;
  }
  try {
    await safetyApi.createMaintenanceTask({ ...taskForm, issueId: taskForm.issueId || null });
    message.success('维护任务创建成功');
    taskVisible.value = false;
    activeTab.value = 'tasks';
    await reload();
  } catch (cause: any) {
    error.value = cause?.message || '维护任务创建失败。';
  }
}
async function startTask(row: SafetyMaintenanceTaskVo) {
  try {
    await safetyApi.startMaintenanceTask(row.id);
    message.success('维护任务已开始');
    await loadTasks();
  } catch (cause: any) {
    error.value = cause?.message || '任务开始失败。';
  }
}
async function completeTask() {
  if (!selectedTask.value || !completeForm.content) return;
  let measurements: Record<string, unknown>;
  let standardLimits: Record<string, unknown>;
  let parts: Array<Record<string, unknown>>;
  try {
    measurements = parseObject(measurementsText.value, '检测数据');
    standardLimits = parseObject(limitsText.value, '标准限值');
    parts = parseArray(partsText.value, '更换零件');
    completeForm.compliant = calculatedCompliance(measurements, standardLimits) ?? completeForm.compliant;
  } catch (cause: any) {
    error.value = cause.message;
    return;
  }
  try {
    await safetyApi.completeMaintenanceTask(selectedTask.value.id, {
      ...completeForm,
      measurements,
      standardLimits,
      parts,
      partsCostCents: Math.round(partsCostYuan.value * 100),
      laborCostCents: Math.round(laborCostYuan.value * 100),
    });
    message.success('维护任务已完成');
    completeVisible.value = false;
    await reload();
  } catch (cause: any) {
    error.value = cause?.message || '维护结果提交失败。';
  }
}
onMounted(reload);
</script>

<style scoped>
.maintenance-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 12px;
  box-sizing: border-box;
  overflow: hidden;
}
.safety-tabs,
.safety-tabs :deep(.n-tabs-pane-wrapper),
.safety-tabs :deep(.n-tab-pane),
.safety-page-table {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}
.state-alert {
  margin-bottom: 12px;
}
.unit {
  margin-left: 8px;
  color: var(--text-color-3);
}
:deep(.overdue) {
  color: #dc2626;
  font-weight: 600;
}
@media (max-width: 720px) {
  .maintenance-page {
    padding: 8px;
  }
  :deep(.n-card-header) {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
