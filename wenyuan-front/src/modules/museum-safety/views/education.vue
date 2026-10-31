<template>
  <SafetyPageGuard>
    <div class="education-page" aria-label="安全预案与教育培训">
      <NTabs
        v-model:value="activeTab"
        type="line"
        animated
        class="safety-tabs"
        pane-wrapper-style="display: flex; flex: 1; min-height: 0; overflow: hidden;"
        pane-style="display: flex; flex: 1; min-height: 0; flex-direction: column; overflow: hidden;"
      >
        <NTabPane name="documents" tab="预案与学习资料">
          <SafetyDocumentManager title="安全预案与教育资料" :allowed-types="documentTypes" />
        </NTabPane>
        <NTabPane name="training" tab="培训与考核">
          <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
          <HPageDataTable
            ref="tableRef"
            title="教育培训"
            class="safety-page-table"
            :columns="columns"
            :fetch-data="fetchPlans"
            :params="tableQuery"
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
                <VenueSelect v-model:value="orgId" clearable placeholder="全部场馆" style="width: 240px" />
              </NFormItem>
            </template>
            <template #toolbar-extra>
              <NButton v-if="canTrain" type="primary" @click="openCreate">新增培训计划</NButton>
            </template>
          </HPageDataTable>
        </NTabPane>
      </NTabs>

      <HPageForm
        v-model:show="editVisible"
        title="新增培训计划"
        :model="form"
        :auto-close="false"
        :handle-submit="savePlan"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect v-model:value="form.orgId" placeholder="请选择场馆" @update:value="loadDocuments"
        /></NFormItem>
        <NFormItem label="计划编码"><NInput value="保存后自动生成" disabled /></NFormItem
        ><NFormItem label="计划名称" required><NInput v-model:value="form.name" /></NFormItem>
        <NFormItem label="制定部门" required
          ><GroupSelect v-model:value="form.departmentId" placeholder="请选择系统组织" /></NFormItem
        ><NFormItem label="培训方式"><NSelect v-model:value="form.mode" :options="modeOptions" /></NFormItem>
        <NFormItem label="执行周期" required><NDatePicker v-model:value="range" type="datetimerange" /></NFormItem>
        <NFormItem label="参与人员" required
          ><UserSelect v-model:value="participantIds" multiple clearable placeholder="请选择参与人员"
        /></NFormItem>
        <NFormItem label="关联资料"
          ><NSelect v-model:value="form.documentIds" :options="documentOptions" multiple filterable clearable
        /></NFormItem>
        <NFormItem label="课程清单" required
          ><NInput v-model:value="courseText" type="textarea" placeholder="每行一项课程"
        /></NFormItem>
        <NFormItem label="考核方式" required
          ><HDictSelect
            v-model:value="form.assessmentMethod"
            type="museum_safety_assessment_method"
            :fallback-options="assessmentMethodOptions"
            placeholder="请选择考核方式" /></NFormItem
        ><NFormItem label="合格分数"
          ><NInputNumber v-model:value="form.passScore" :min="0" :max="100" clearable
        /></NFormItem>
      </HPageForm>

      <NDrawer v-model:show="recordsVisible" :width="760">
        <NDrawerContent :title="`${selectedPlan?.name || ''} · 学习记录`" closable>
          <div class="drawer-toolbar">
            <NButton v-if="canTrain" type="primary" @click="openRecord">登记学习结果</NButton>
          </div>
          <NDataTable
            :columns="recordColumns"
            :data="records"
            :loading="recordLoading"
            :row-key="(row) => row.id"
            :pagination="{ pageSize: 20 }"
          />
        </NDrawerContent>
      </NDrawer>

      <HPageForm
        v-model:show="recordVisible"
        title="登记学习考核结果"
        :model="recordForm"
        :auto-close="false"
        :handle-submit="saveRecord"
        label-placement="left"
        label-width="100"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="学员" required
          ><NSelect
            v-model:value="recordForm.studentId"
            :options="participantOptions"
            filterable
            clearable
            placeholder="从计划参与人员中选择" /></NFormItem
        ><NFormItem label="开始时间"><NDatePicker v-model:value="recordForm.startedAt" type="datetime" /></NFormItem>
        <NFormItem label="完成时间"
          ><NDatePicker v-model:value="recordForm.completedAt" type="datetime" clearable /></NFormItem
        ><NFormItem label="学习时长"
          ><NInputNumber v-model:value="recordForm.durationMinutes" :min="0" /><span class="unit">分钟</span></NFormItem
        >
        <NFormItem label="测试成绩"
          ><NInputNumber v-model:value="recordForm.score" :min="0" :max="100" clearable /></NFormItem
        ><NFormItem label="考核状态"
          ><NSelect v-model:value="recordForm.status" :options="assessmentOptions"
        /></NFormItem>
        <NFormItem label="学习凭证"><SafetyAttachmentUpload v-model="recordForm.attachments" /></NFormItem>
      </HPageForm>
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref } from 'vue';
import {
  NAlert,
  NButton,
  NDataTable,
  NDatePicker,
  NDrawer,
  NDrawerContent,
  NFormItem,
  NInput,
  NInputNumber,
  NProgress,
  NSelect,
  NSpace,
  NTabPane,
  NTabs,
  NTag,
  type DataTableColumns,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { get as getUser } from '@/modules/sys/api/user';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import HDictSelect from '@/components/form/field/dict/select.vue';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import { safetyApi, unwrapResponse } from '../api';
import type {
  SafetyAssessmentStatus,
  SafetyDocumentType,
  SafetyDocumentVo,
  SafetyLearningRecordVo,
  SafetyTrainingMode,
  SafetyTrainingPlanVo,
} from '../typings';
import SafetyDocumentManager from '../components/SafetyDocumentManager.vue';
import SafetyAttachmentUpload from '../components/SafetyAttachmentUpload.vue';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';

const documentTypes: SafetyDocumentType[] = ['EMERGENCY_PLAN', 'EDUCATION_POLICY', 'REGULATION', 'OPERATING_MANUAL'];
const message = useMessage();
const authStore = useAuthStore();
const canTrain = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_TRAINING'));
const activeTab = ref('documents');
const orgId = ref<string | null>(null);
const recordLoading = ref(false);
const tableRef = ref<any>(null);
const error = ref('');
const tableQuery = computed(() => ({ orgId: orgId.value || undefined }));
const documents = ref<SafetyDocumentVo[]>([]);
const records = ref<SafetyLearningRecordVo[]>([]);
const selectedPlan = ref<SafetyTrainingPlanVo | null>(null);
const participantNames = ref<Record<string, string>>({});
const editVisible = ref(false);
const recordsVisible = ref(false);
const recordVisible = ref(false);
const modeOptions = [
  { label: '课程', value: 'COURSE' },
  { label: '法规学习', value: 'LAW' },
  { label: '制度学习', value: 'POLICY' },
  { label: '手册学习', value: 'MANUAL' },
  { label: '安全演练', value: 'DRILL' },
];
const assessmentOptions = [
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '学习中', value: 'LEARNING' },
  { label: '已通过', value: 'PASSED' },
  { label: '未通过', value: 'FAILED' },
];
const assessmentMethodOptions = [
  { label: '在线测试', value: '在线测试' },
  { label: '现场实操', value: '现场实操' },
  { label: '书面考试', value: '书面考试' },
  { label: '演练考核', value: '演练考核' },
];
const documentOptions = computed(() =>
  documents.value.map((item) => ({ label: `${item.code} · ${item.title}`, value: item.id })),
);
const participantOptions = computed(() =>
  (selectedPlan.value?.participantIds || []).map((id) => ({
    label: participantNames.value[id] || `用户 ${id}`,
    value: id,
  })),
);
const form = reactive({
  orgId: null as string | null,
  code: '',
  name: '',
  departmentId: null as string | null,
  mode: 'COURSE' as SafetyTrainingMode,
  documentIds: [] as string[],
  assessmentMethod: '',
  passScore: null as number | null,
});
const range = ref<[number, number] | null>(null);
const participantIds = ref<string[]>([]);
const courseText = ref('');
const recordForm = reactive({
  studentId: '',
  startedAt: Date.now(),
  completedAt: null as number | null,
  durationMinutes: 0,
  score: null as number | null,
  status: 'NOT_STARTED' as SafetyAssessmentStatus,
  attachments: [] as string[],
});
const columns: DataTableColumns<SafetyTrainingPlanVo> = [
  { title: '计划编码', key: 'code', width: 140 },
  { title: '计划名称', key: 'name', minWidth: 180 },
  { title: '部门', key: 'department', width: 140 },
  {
    title: '方式',
    key: 'mode',
    width: 110,
    render: (r) => modeOptions.find((i) => i.value === r.mode)?.label || r.mode,
  },
  { title: '开始', key: 'startsAt', width: 170, render: (r) => formatDateTime(r.startsAt) },
  { title: '结束', key: 'endsAt', width: 170, render: (r) => formatDateTime(r.endsAt) },
  { title: '参与人数', key: 'participantIds', width: 100, render: (r) => r.participantIds.length },
  {
    title: '完成率',
    key: 'completionRate',
    width: 160,
    render: (r) =>
      h(NProgress, { percentage: Math.round(r.completionRate * 100), height: 10, indicatorPlacement: 'inside' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 110,
    fixed: 'right',
    render: (r) =>
      h(NButton, { text: true, type: 'primary', onClick: () => showRecords(r) }, { default: () => '学习记录' }),
  },
];
const recordColumns: DataTableColumns<SafetyLearningRecordVo> = [
  { title: '学员', key: 'studentId', width: 140, render: (r) => participantNames.value[r.studentId] || r.studentId },
  { title: '开始时间', key: 'startedAt', width: 180, render: (r) => formatDateTime(r.startedAt) },
  {
    title: '完成时间',
    key: 'completedAt',
    width: 180,
    render: (r) => (r.completedAt ? formatDateTime(r.completedAt) : '--'),
  },
  { title: '时长', key: 'durationMinutes', width: 100, render: (r) => `${r.durationMinutes} 分钟` },
  { title: '成绩', key: 'score', width: 90, render: (r) => r.score ?? '--' },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (r) =>
      h(
        NTag,
        { type: r.status === 'PASSED' ? 'success' : r.status === 'FAILED' ? 'error' : 'warning', bordered: false },
        { default: () => assessmentOptions.find((i) => i.value === r.status)?.label || r.status },
      ),
  },
];
function list(value: string, delimiter: RegExp) {
  return value
    .split(delimiter)
    .map((v) => v.trim())
    .filter(Boolean);
}
async function loadDocuments(value: string | null = form.orgId) {
  try {
    const result = unwrapResponse<SafetyDocumentVo[]>(
      await safetyApi.managementDocuments({ orgId: value || undefined }),
    );
    documents.value = (result || []).filter((item) => documentTypes.includes(item.type) && item.status === 'EFFECTIVE');
  } catch {
    documents.value = [];
  }
}
async function fetchPlans(params: Record<string, unknown>) {
  error.value = '';
  try {
    return await safetyApi.trainingPlanPage(params);
  } catch (cause: any) {
    error.value = cause?.message || '培训计划加载失败。';
    throw cause;
  }
}
function refreshTable() {
  tableRef.value?.handleRefresh?.();
}
function openCreate() {
  Object.assign(form, {
    orgId: orgId.value,
    code: '',
    name: '',
    departmentId: null,
    mode: 'COURSE',
    documentIds: [],
    assessmentMethod: '',
    passScore: null,
  });
  range.value = null;
  participantIds.value = [];
  courseText.value = '';
  void loadDocuments();
  editVisible.value = true;
}
async function savePlan() {
  if (
    !form.orgId ||
    !form.departmentId ||
    !form.name ||
    !range.value ||
    !participantIds.value.length ||
    !courseText.value
  ) {
    error.value = '请完整填写场馆、计划、周期、参与人员和课程。';
    return;
  }
  try {
    await safetyApi.createTrainingPlan({
      ...form,
      departmentId: form.departmentId!,
      startsAt: range.value[0],
      endsAt: range.value[1],
      participantIds: participantIds.value,
      courses: list(courseText.value, /\n+/),
    });
    message.success('培训计划创建成功');
    editVisible.value = false;
    refreshTable();
  } catch (cause: any) {
    error.value = cause?.message || '培训计划创建失败。';
  }
}
async function showRecords(row: SafetyTrainingPlanVo) {
  selectedPlan.value = row;
  recordsVisible.value = true;
  recordLoading.value = true;
  try {
    const [recordResult, names] = await Promise.all([
      safetyApi.learningRecords(row.id),
      Promise.allSettled(
        row.participantIds.map(async (id) => {
          const user = unwrapResponse<{ name?: string }>(await getUser(id));
          return { id, name: user?.name || id };
        }),
      ),
    ]);
    records.value = unwrapResponse<SafetyLearningRecordVo[]>(recordResult) || [];
    participantNames.value = Object.fromEntries(
      names
        .filter(
          (result): result is PromiseFulfilledResult<{ id: string; name: string }> => result.status === 'fulfilled',
        )
        .map((result) => [result.value.id, result.value.name]),
    );
  } catch (cause: any) {
    error.value = cause?.message || '学习记录加载失败。';
  } finally {
    recordLoading.value = false;
  }
}
function openRecord() {
  Object.assign(recordForm, {
    studentId: '',
    startedAt: Date.now(),
    completedAt: null,
    durationMinutes: 0,
    score: null,
    status: 'NOT_STARTED',
    attachments: [],
  });
  recordVisible.value = true;
}
async function saveRecord() {
  if (!selectedPlan.value || !recordForm.studentId) return;
  try {
    await safetyApi.recordLearning({ planId: selectedPlan.value.id, ...recordForm });
    message.success('学习记录已保存');
    recordVisible.value = false;
    await showRecords(selectedPlan.value);
    refreshTable();
  } catch (cause: any) {
    error.value = cause?.message || '学习记录保存失败。';
  }
}
</script>

<style scoped>
.education-page {
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
.safety-tabs {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}
.safety-tabs :deep(.n-tabs-nav) {
  flex: none;
}
.safety-tabs :deep(.n-tabs-pane-wrapper),
.safety-tabs :deep(.n-tab-pane),
.safety-page-table {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}
.safety-tabs :deep(.document-manager) {
  padding: 0;
}
.state-alert {
  margin-bottom: 12px;
}
.unit {
  margin-left: 8px;
  color: var(--text-color-3);
}
.drawer-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
@media (max-width: 720px) {
  .education-page {
    padding: 8px;
  }
  :deep(.n-card-header) {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  :deep(.n-card-header__extra .n-space) {
    flex-wrap: wrap;
  }
}
</style>
