<template>
  <div class="document-manager">
    <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
    <HPageDataTable
      ref="tableRef"
      :title="title"
      class="safety-page-table"
      :columns="columns"
      :fetch-data="fetchDocuments"
      :params="filters"
      :scroll-x="1260"
      row-key-field="id"
      :is-add="false"
      :is-edit="false"
      :is-copy="false"
      :is-delete="false"
      :is-export="false"
      :show-operate="false"
      show-query-reset-button
      query-configurable
    >
      <template #queryForm>
        <NFormItem label="所属场馆">
          <VenueSelect v-model:value="filters.orgId" clearable placeholder="全部场馆" style="width: 240px" />
        </NFormItem>
        <NFormItem v-if="allowedTypes.length > 1" label="文档类型">
          <HDictSelect
            v-model:value="filters.type"
            type="museum_safety_document_type"
            :fallback-options="typeOptions"
            :include-values="allowedTypes"
            clearable
            placeholder="全部类别"
            style="width: 180px"
          />
        </NFormItem>
        <NFormItem label="文档状态">
          <HDictSelect
            v-model:value="filters.status"
            type="museum_safety_document_status"
            :fallback-options="statusOptions"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          />
        </NFormItem>
      </template>
      <template #toolbar-extra>
        <NSpace>
          <HDialogImportExcelForm
            v-if="canGovern"
            upload-label="点击或拖动制度导入表到此处"
            upload-desc="请先下载模板填写。场馆使用名称或编码，责任人使用登录账号；系统会在导入前完成字段解析与逐行校验。"
            tooltip-text="导入制度"
            export-label="下载填写模板"
            accept=".xlsx,.xls,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            :multiple="false"
            :show-file-list="false"
            :local-file-handler="importDocuments"
            :template-handler="downloadImportTemplate"
            @finish="handleImportFinished"
            @finish-error="handleImportError"
          >
            <template #trigger="{ openDialog }">
              <NButton @click.stop="openDialog">导入制度</NButton>
            </template>
          </HDialogImportExcelForm>
          <NButton :loading="exporting" @click="exportDocuments">导出 Excel</NButton>
          <NButton v-if="canGovern" @click="openScopeAccess">类型/目录权限</NButton>
          <NButton v-if="canGovern" type="primary" @click="openCreate">新增文档</NButton>
        </NSpace>
      </template>
    </HPageDataTable>

    <HPageForm
      v-model:show="editVisible"
      :title="editMode === 'create' ? `新增${title}` : '创建新版本'"
      :model="editMode === 'create' ? documentForm : versionForm"
      :auto-close="false"
      :handle-submit="saveDocument"
      label-placement="left"
      label-width="110"
      submit-text="保存"
    >
      <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
      <template v-if="editMode === 'create'">
        <NFormItem label="所属场馆" required
          ><VenueSelect v-model:value="documentForm.orgId" placeholder="请选择场馆"
        /></NFormItem>
        <NFormItem label="文档类型" required
          ><HDictSelect
            v-model:value="documentForm.type"
            type="museum_safety_document_type"
            :fallback-options="typeOptions"
            :include-values="allowedTypes"
        /></NFormItem>
        <NFormItem label="业务领域"
          ><HDictSelect
            v-model:value="documentForm.domain"
            type="museum_safety_domain"
            :fallback-options="domainOptions"
            clearable
        /></NFormItem>
        <NFormItem label="制度编码"
          ><NInput v-model:value="documentForm.code" placeholder="留空后由系统自动生成"
        /></NFormItem>
        <NFormItem label="制度名称" required><NInput v-model:value="documentForm.title" /></NFormItem>
        <NFormItem label="制度分类" required
          ><HDictSelect
            v-model:value="documentForm.category"
            type="museum_safety_document_category"
            :fallback-options="categoryOptions"
            placeholder="请选择制度分类"
        /></NFormItem>
        <NFormItem label="适用范围" required><NInput v-model:value="documentForm.applicableScope" /></NFormItem>
        <NFormItem label="责任人" required
          ><UserSelect v-model:value="documentForm.ownerUserId" clearable placeholder="请选择责任人"
        /></NFormItem>
        <NFormItem label="密级"
          ><HDictSelect
            v-model:value="documentForm.confidentiality"
            type="museum_safety_confidentiality"
            :fallback-options="confidentialityOptions"
        /></NFormItem>
        <NFormItem label="审核级数"
          ><NInputNumber v-model:value="documentForm.requiredApprovals" :min="1" :max="10"
        /></NFormItem>
      </template>
      <NFormItem label="正文" required
        ><NInput v-model:value="versionForm.content" type="textarea" :autosize="{ minRows: 8, maxRows: 18 }"
      /></NFormItem>
      <NFormItem label="变更说明" required
        ><NInput v-model:value="versionForm.changeSummary" type="textarea"
      /></NFormItem>
      <NFormItem label="附件"><SafetyAttachmentUpload v-model="attachmentRefs" /></NFormItem>
      <NFormItem label="标准依据"
        ><NInput v-model:value="basisText" placeholder="例如：GB/T 16571-2012|第5章；GB 50116|第6章"
      /></NFormItem>
      <NFormItem label="生效时间"
        ><NDatePicker v-model:value="versionForm.effectiveAt" type="datetime" clearable
      /></NFormItem>
      <NFormItem label="失效时间"
        ><NDatePicker v-model:value="versionForm.expiresAt" type="datetime" clearable
      /></NFormItem>
    </HPageForm>

    <NDrawer v-model:show="detailVisible" :width="820">
      <NDrawerContent :title="detail?.title || '文档详情'" closable>
        <NSpin :show="detailLoading">
          <template v-if="detail">
            <NDescriptions bordered :column="2" label-placement="left">
              <NDescriptionsItem label="编码">{{ detail.code }}</NDescriptionsItem>
              <NDescriptionsItem label="状态">{{ statusLabel(detail.status) }}</NDescriptionsItem>
              <NDescriptionsItem label="类型">{{ typeLabel(detail.type) }}</NDescriptionsItem>
              <NDescriptionsItem label="当前版本">{{ detail.latestVersion.versionNo }}</NDescriptionsItem>
              <NDescriptionsItem label="适用范围">{{ detail.applicableScope }}</NDescriptionsItem>
              <NDescriptionsItem label="责任人">{{ detail.ownerUserId }}</NDescriptionsItem>
              <NDescriptionsItem label="正文" :span="2"
                ><div class="document-content">{{ detail.latestVersion.content }}</div></NDescriptionsItem
              >
              <NDescriptionsItem label="标准依据" :span="2">{{
                basisLabel(detail.latestVersion.complianceBasis)
              }}</NDescriptionsItem>
            </NDescriptions>
            <NSpace class="detail-actions">
              <NButton v-if="canGovern" @click="openVersion(detail)">创建新版本</NButton>
              <NButton
                v-if="canGovern && canSubmit(detail.latestVersion.status)"
                type="primary"
                @click="submitVersion(detail)"
                >提交审核</NButton
              >
              <NButton
                v-if="canApprove && detail.latestVersion.status === 'UNDER_REVIEW'"
                type="warning"
                @click="openReview(detail)"
                >审核</NButton
              >
              <NButton v-if="canGovern" @click="openAccess(detail)">权限配置</NButton>
            </NSpace>
            <NDivider>版本与审核记录</NDivider>
            <NCollapse>
              <NCollapseItem
                v-for="version in detail.versions"
                :key="version.id"
                :title="`${version.versionNo} · ${statusLabel(version.status)}`"
              >
                <p>{{ version.changeSummary }}</p>
                <p class="muted">
                  提交时间：{{ version.submittedAt ? formatDateTime(version.submittedAt) : '--' }}；通过数：{{
                    version.approvalCount
                  }}/{{ detail.requiredApprovals }}
                </p>
                <NTimeline v-if="version.reviews.length">
                  <NTimelineItem
                    v-for="review in version.reviews"
                    :key="review.id"
                    :title="`${decisionLabel(review.decision)} · ${review.reviewerId}`"
                    :time="formatDateTime(review.reviewedAt)"
                  >
                    <div>{{ review.opinion }}</div>
                    <div v-if="review.annotations.length" class="review-annotations">
                      批注：{{ review.annotations.join('；') }}
                    </div>
                  </NTimelineItem>
                </NTimeline>
              </NCollapseItem>
            </NCollapse>
          </template>
        </NSpin>
      </NDrawerContent>
    </NDrawer>

    <NModal v-model:show="reviewVisible" preset="card" title="审核文档" class="small-dialog">
      <NForm label-placement="left" label-width="90">
        <NFormItem label="决定"
          ><HDictSelect
            v-model:value="reviewForm.decision"
            type="museum_safety_review_decision"
            :fallback-options="decisionOptions"
        /></NFormItem>
        <NFormItem label="审核意见" required><NInput v-model:value="reviewForm.opinion" type="textarea" /></NFormItem>
        <NFormItem label="批注"><NInput v-model:value="annotationText" placeholder="多条批注用分号分隔" /></NFormItem>
        <div class="form-actions">
          <NButton @click="reviewVisible = false">取消</NButton
          ><NButton type="primary" :loading="submitting" @click="reviewVersion">提交</NButton>
        </div>
      </NForm>
    </NModal>

    <NModal v-model:show="accessVisible" preset="card" title="文档权限配置" class="small-dialog">
      <NAlert type="info" :bordered="false" class="state-alert"
        >保存后以当前规则整体替换；不配置规则时仍按模块权限与责任人规则访问。</NAlert
      >
      <NSpin :show="accessLoading">
        <div v-for="(rule, index) in accessRules" :key="index" class="access-rule">
          <HDictSelect
            v-model:value="rule.subjectType"
            type="museum_safety_access_subject_type"
            :fallback-options="subjectTypeOptions"
            class="subject-type"
            @update:value="resetAccessSubject(rule)"
          />
          <HRoleSelect
            v-if="rule.subjectType === 'ROLE'"
            v-model:value="rule.subjectId"
            clearable
            placeholder="请选择角色"
          />
          <UserSelect v-else v-model:value="rule.subjectId" clearable placeholder="请选择用户" />
          <HDictSelect
            v-model:value="rule.actions"
            type="museum_safety_access_action"
            :fallback-options="actionOptions"
            multiple
            return-array
            placeholder="授权项"
          />
          <NButton type="error" secondary @click="removeAccessRule(index)">移除</NButton>
        </div>
        <NButton dashed block @click="addAccessRule">添加权限规则</NButton>
        <div class="form-actions">
          <NButton @click="accessVisible = false">取消</NButton
          ><NButton type="primary" :loading="submitting" @click="saveAccess">保存</NButton>
        </div>
      </NSpin>
    </NModal>

    <NModal v-model:show="scopeAccessVisible" preset="card" title="类型/目录权限配置" class="dialog-card">
      <NAlert type="info" :bordered="false" class="state-alert"
        >单个文档权限优先于类型/目录规则；未配置任何规则时沿用模块权限。</NAlert
      >
      <NForm label-placement="left" label-width="100">
        <NFormItem label="所属场馆" required
          ><VenueSelect v-model:value="scopeAccessForm.orgId" placeholder="请选择场馆"
        /></NFormItem>
        <NFormItem label="范围层级"
          ><HDictSelect
            v-model:value="scopeAccessForm.scopeType"
            type="museum_safety_access_scope"
            :fallback-options="scopeTypeOptions"
        /></NFormItem>
        <NFormItem label="范围值" required>
          <HDictSelect
            v-if="scopeAccessForm.scopeType === 'TYPE'"
            v-model:value="scopeAccessForm.scopeValue"
            type="museum_safety_document_type"
            :fallback-options="typeOptions"
            :include-values="allowedTypes"
          />
          <HDictSelect
            v-else
            v-model:value="scopeAccessForm.scopeValue"
            type="museum_safety_document_category"
            :fallback-options="categoryOptions"
            placeholder="请选择制度分类"
          />
        </NFormItem>
        <NFormItem label="已有规则"
          ><NButton :loading="scopeAccessLoading" @click="loadScopeAccess">加载当前范围规则</NButton></NFormItem
        >
      </NForm>
      <NSpin :show="scopeAccessLoading">
        <div v-for="(rule, index) in scopeAccessRules" :key="index" class="access-rule">
          <HDictSelect
            v-model:value="rule.subjectType"
            type="museum_safety_access_subject_type"
            :fallback-options="subjectTypeOptions"
            class="subject-type"
            @update:value="resetAccessSubject(rule)"
          />
          <HRoleSelect
            v-if="rule.subjectType === 'ROLE'"
            v-model:value="rule.subjectId"
            clearable
            placeholder="请选择角色"
          />
          <UserSelect v-else v-model:value="rule.subjectId" clearable placeholder="请选择用户" />
          <HDictSelect
            v-model:value="rule.actions"
            type="museum_safety_access_action"
            :fallback-options="actionOptions"
            multiple
            return-array
            placeholder="授权项"
          />
          <NButton type="error" secondary @click="removeScopeAccessRule(index)">移除</NButton>
        </div>
        <NButton dashed block @click="addScopeAccessRule">添加权限规则</NButton>
        <div class="form-actions">
          <NButton @click="scopeAccessVisible = false">取消</NButton
          ><NButton type="primary" :loading="submitting" @click="saveScopeAccess">保存</NButton>
        </div>
      </NSpin>
    </NModal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref } from 'vue';
import * as XLSX from 'xlsx';
import {
  NAlert,
  NButton,
  NCollapse,
  NCollapseItem,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NDrawer,
  NDrawerContent,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NPopconfirm,
  NSpace,
  NSpin,
  NTag,
  NTimeline,
  NTimelineItem,
  type DataTableColumns,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import HRoleSelect from '@/modules/sys/components/form/field/RoleSelect.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { search as searchUsers } from '@/modules/sys/api/user';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import type { MuseumVenueVo } from '../../museum-device/typings';
import { loadVenueCatalog } from '../../museum-device/utils/venueCatalog';
import { safetyApi, unwrapResponse } from '../api';
import SafetyAttachmentUpload from './SafetyAttachmentUpload.vue';
import type {
  SafetyAccessAction,
  SafetyAccessSubjectType,
  SafetyComplianceBasis,
  SafetyConfidentiality,
  SafetyDocumentAccessRuleCommand,
  SafetyDocumentAccessRuleVo,
  SafetyDocumentAccessScope,
  SafetyDocumentCommand,
  SafetyDocumentScopeAccessRuleVo,
  SafetyDocumentStatus,
  SafetyDocumentType,
  SafetyDocumentVo,
  SafetyDocumentVersionCommand,
  SafetyDomain,
  SafetyReviewDecision,
} from '../typings';

const props = defineProps<{ title: string; allowedTypes: SafetyDocumentType[] }>();
const message = useMessage();
const authStore = useAuthStore();
const canGovern = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_GOVERNANCE'));
const canApprove = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_APPROVE'));
const submitting = ref(false);
const exporting = ref(false);
const tableRef = ref<any>(null);
const detailLoading = ref(false);
const accessLoading = ref(false);
const scopeAccessLoading = ref(false);
const error = ref('');
const detail = ref<SafetyDocumentVo | null>(null);
const editVisible = ref(false);
const detailVisible = ref(false);
const reviewVisible = ref(false);
const accessVisible = ref(false);
const scopeAccessVisible = ref(false);
const editMode = ref<'create' | 'version'>('create');
const filters = reactive<{
  orgId: string | null;
  type: SafetyDocumentType | null;
  status: SafetyDocumentStatus | null;
}>({ orgId: null, type: null, status: null });

const allTypeOptions: Array<{ label: string; value: SafetyDocumentType }> = [
  { label: '安全制度', value: 'SAFETY_POLICY' },
  { label: '安全预案', value: 'EMERGENCY_PLAN' },
  { label: '教育制度', value: 'EDUCATION_POLICY' },
  { label: '法律法规', value: 'REGULATION' },
  { label: '操作手册', value: 'OPERATING_MANUAL' },
];
const typeOptions = computed(() => allTypeOptions.filter((item) => props.allowedTypes.includes(item.value)));
const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '审核中', value: 'UNDER_REVIEW' },
  { label: '已生效', value: 'EFFECTIVE' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已归档', value: 'ARCHIVED' },
];
const domainOptions: Array<{ label: string; value: SafetyDomain }> = [
  { label: '建筑结构', value: 'BUILDING' },
  { label: '防雷', value: 'LIGHTNING' },
  { label: '消防', value: 'FIRE' },
  { label: '安防', value: 'SECURITY' },
  { label: '用电', value: 'ELECTRICITY' },
  { label: '建筑环境', value: 'BUILDING_ENVIRONMENT' },
];
const confidentialityOptions: Array<{ label: string; value: SafetyConfidentiality }> = [
  { label: '公开', value: 'PUBLIC' },
  { label: '内部', value: 'INTERNAL' },
  { label: '受限', value: 'RESTRICTED' },
];
const categoryOptions = [
  { label: '未分类', value: '未分类' },
  { label: '安防制度', value: '安防制度' },
  { label: '消防制度', value: '消防制度' },
  { label: '防雷制度', value: '防雷制度' },
  { label: '值班巡检', value: '值班巡检' },
  { label: '应急预案', value: '应急预案' },
  { label: '教育培训', value: '教育培训' },
  { label: '法律法规', value: '法律法规' },
  { label: '操作手册', value: '操作手册' },
];
const decisionOptions = [
  { label: '通过', value: 'APPROVE' },
  { label: '驳回', value: 'REJECT' },
  { label: '仅批注', value: 'COMMENT' },
];
const subjectTypeOptions = [
  { label: '角色', value: 'ROLE' },
  { label: '用户', value: 'USER' },
];
const actionOptions = [
  { label: '查看', value: 'VIEW' },
  { label: '修改', value: 'EDIT' },
  { label: '删除', value: 'DELETE' },
  { label: '审核', value: 'REVIEW' },
  { label: '下载', value: 'DOWNLOAD' },
];
const scopeTypeOptions = [
  { label: '文档类型', value: 'TYPE' },
  { label: '制度目录/分类', value: 'CATEGORY' },
];

const documentForm = reactive({
  orgId: null as string | null,
  type: props.allowedTypes[0],
  domain: null as SafetyDomain | null,
  code: '',
  title: '',
  category: '',
  applicableScope: '',
  ownerUserId: '',
  confidentiality: 'INTERNAL' as SafetyConfidentiality,
  requiredApprovals: 1,
});
const versionForm = reactive({
  content: '',
  changeSummary: '',
  effectiveAt: null as number | null,
  expiresAt: null as number | null,
});
const attachmentRefs = ref<string[]>([]);
const basisText = ref('GB/T 16571-2012|博物馆和文物保护单位安全防范系统要求');
const reviewForm = reactive({ decision: 'APPROVE' as SafetyReviewDecision, opinion: '' });
const annotationText = ref('');
const accessRules = ref<SafetyDocumentAccessRuleCommand[]>([]);
const scopeAccessRules = ref<SafetyDocumentAccessRuleCommand[]>([]);
const scopeAccessForm = reactive({
  orgId: null as string | null,
  scopeType: 'TYPE' as SafetyDocumentAccessScope,
  scopeValue: props.allowedTypes[0] as string,
});

const columns: DataTableColumns<SafetyDocumentVo> = [
  { title: '编码', key: 'code', width: 150 },
  { title: '名称', key: 'title', minWidth: 220 },
  { title: '类型', key: 'type', width: 120, render: (row) => typeLabel(row.type) },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) =>
      h(
        NTag,
        {
          type:
            row.status === 'EFFECTIVE'
              ? 'success'
              : row.status === 'REJECTED'
                ? 'error'
                : row.status === 'UNDER_REVIEW'
                  ? 'warning'
                  : 'default',
          bordered: false,
        },
        { default: () => statusLabel(row.status) },
      ),
  },
  { title: '版本', key: 'currentRevision', width: 100, render: (row) => row.latestVersion.versionNo },
  { title: '责任人', key: 'ownerUserId', width: 140 },
  { title: '适用范围', key: 'applicableScope', minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 270,
    fixed: 'right',
    render: (row) =>
      h(NSpace, null, {
        default: () => [
          h(NButton, { text: true, type: 'primary', onClick: () => showDetail(row) }, { default: () => '详情' }),
          h(NButton, { text: true, onClick: () => downloadDocument(row) }, { default: () => '下载' }),
          canGovern.value
            ? h(NButton, { text: true, onClick: () => openVersion(row) }, { default: () => '新版本' })
            : null,
          canGovern.value && row.status !== 'UNDER_REVIEW' && row.status !== 'ARCHIVED'
            ? h(
                NPopconfirm,
                { onPositiveClick: () => archive(row) },
                {
                  trigger: () => h(NButton, { text: true, type: 'error' }, { default: () => '归档' }),
                  default: () => '确认归档该文档？',
                },
              )
            : null,
        ],
      }),
  },
];

function parseList(value: string, delimiter = /[,，]/) {
  return value
    .split(delimiter)
    .map((item) => item.trim())
    .filter(Boolean);
}
function parseBasis(): SafetyComplianceBasis[] {
  return parseList(basisText.value, /[;；]/).map((item) => {
    const [code, title = code, clause] = item.split('|').map((part) => part.trim());
    return { code, title, clause: clause || null };
  });
}
function basisLabel(items: SafetyComplianceBasis[]) {
  return items.length ? items.map((item) => `${item.code}${item.clause ? ` ${item.clause}` : ''}`).join('；') : '--';
}
function typeLabel(value: SafetyDocumentType) {
  return allTypeOptions.find((item) => item.value === value)?.label || value;
}
function statusLabel(value: SafetyDocumentStatus) {
  return statusOptions.find((item) => item.value === value)?.label || value;
}
function decisionLabel(value: SafetyReviewDecision) {
  return decisionOptions.find((item) => item.value === value)?.label || value;
}
function canSubmit(status: SafetyDocumentStatus) {
  return status === 'DRAFT';
}
function resetForms() {
  Object.assign(documentForm, {
    orgId: filters.orgId,
    type: filters.type || props.allowedTypes[0],
    domain: null,
    code: '',
    title: '',
    category: '',
    applicableScope: '',
    ownerUserId: '',
    confidentiality: 'INTERNAL',
    requiredApprovals: 1,
  });
  Object.assign(versionForm, { content: '', changeSummary: '', effectiveAt: null, expiresAt: null });
  attachmentRefs.value = [];
  basisText.value = 'GB/T 16571-2012|博物馆和文物保护单位安全防范系统要求';
}
function openCreate() {
  resetForms();
  editMode.value = 'create';
  editVisible.value = true;
}
function openVersion(row: SafetyDocumentVo) {
  detail.value = row;
  editMode.value = 'version';
  Object.assign(versionForm, {
    content: row.latestVersion.content,
    changeSummary: '',
    effectiveAt: row.latestVersion.effectiveAt || null,
    expiresAt: row.latestVersion.expiresAt || null,
  });
  attachmentRefs.value = [...row.latestVersion.attachments];
  basisText.value = row.latestVersion.complianceBasis
    .map((item) => `${item.code}|${item.title}|${item.clause || ''}`)
    .join('；');
  editVisible.value = true;
}
function openReview(row: SafetyDocumentVo) {
  detail.value = row;
  Object.assign(reviewForm, { decision: 'APPROVE', opinion: '' });
  annotationText.value = '';
  reviewVisible.value = true;
}
async function openAccess(row: SafetyDocumentVo) {
  detail.value = row;
  accessVisible.value = true;
  accessLoading.value = true;
  try {
    const values = unwrapResponse<SafetyDocumentAccessRuleVo[]>(await safetyApi.managementDocumentAccess(row.id));
    accessRules.value = (values || []).map(({ subjectType, subjectId, actions }) => ({
      subjectType,
      subjectId,
      actions: [...actions],
    }));
    if (!accessRules.value.length) addAccessRule();
  } catch (cause: any) {
    error.value = cause?.message || '文档权限加载失败。';
    accessVisible.value = false;
  } finally {
    accessLoading.value = false;
  }
}
function addAccessRule() {
  accessRules.value.push({
    subjectType: 'ROLE' as SafetyAccessSubjectType,
    subjectId: undefined as unknown as string,
    actions: ['VIEW'] as SafetyAccessAction[],
  });
}
function resetAccessSubject(rule: SafetyDocumentAccessRuleCommand) {
  rule.subjectId = undefined as unknown as string;
}
function removeAccessRule(index: number) {
  accessRules.value.splice(index, 1);
}

function openScopeAccess() {
  Object.assign(scopeAccessForm, {
    orgId: filters.orgId,
    scopeType: 'TYPE',
    scopeValue: filters.type || props.allowedTypes[0],
  });
  scopeAccessRules.value = [];
  addScopeAccessRule();
  scopeAccessVisible.value = true;
  if (scopeAccessForm.orgId) void loadScopeAccess();
}
function addScopeAccessRule() {
  scopeAccessRules.value.push({
    subjectType: 'ROLE' as SafetyAccessSubjectType,
    subjectId: undefined as unknown as string,
    actions: ['VIEW'] as SafetyAccessAction[],
  });
}
function removeScopeAccessRule(index: number) {
  scopeAccessRules.value.splice(index, 1);
}
async function loadScopeAccess() {
  if (!scopeAccessForm.orgId || !scopeAccessForm.scopeValue.trim()) {
    error.value = '请选择场馆并填写权限范围值。';
    return;
  }
  scopeAccessLoading.value = true;
  try {
    const values =
      unwrapResponse<SafetyDocumentScopeAccessRuleVo[]>(
        await safetyApi.managementDocumentAccessPolicies({
          orgId: scopeAccessForm.orgId,
          scopeType: scopeAccessForm.scopeType,
        }),
      ) || [];
    scopeAccessRules.value = values
      .filter((item) => item.scopeValue === scopeAccessForm.scopeValue)
      .map(({ subjectType, subjectId, actions }) => ({ subjectType, subjectId, actions: [...actions] }));
    if (!scopeAccessRules.value.length) addScopeAccessRule();
  } catch (cause: any) {
    error.value = cause?.message || '范围权限加载失败。';
  } finally {
    scopeAccessLoading.value = false;
  }
}

function cell(row: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = row[key];
    if (value !== undefined && value !== null && String(value).trim()) return String(value).trim();
  }
  return '';
}

type ImportUser = { id?: string; loginName?: string; name?: string };
type ImportResult = { count: number };

function normalized(value: unknown) {
  return String(value ?? '')
    .trim()
    .toLocaleLowerCase();
}

function responseList<T>(response: any): T[] {
  const data = response?.data?.data ?? response?.data ?? response;
  return Array.isArray(data) ? data : (data?.list ?? []);
}

function parseImportedType(value: string): SafetyDocumentType {
  const key = normalized(value);
  const direct = allTypeOptions.find((item) => normalized(item.value) === key || normalized(item.label) === key)?.value;
  if (!direct || !props.allowedTypes.includes(direct)) throw new Error(`不支持的文档类型：${value}`);
  return direct;
}

function parseImportedOption<T extends string>(
  value: string,
  options: Array<{ label: string; value: T }>,
  field: string,
): T {
  const key = normalized(value);
  const matched = options.find((item) => normalized(item.value) === key || normalized(item.label) === key);
  if (!matched) throw new Error(`${field}“${value}”不在可选值中`);
  return matched.value;
}

function resolveVenue(value: string, venues: MuseumVenueVo[]): MuseumVenueVo {
  const key = normalized(value);
  const idMatch = venues.find((item) => normalized(item.id) === key);
  if (idMatch) return idMatch;
  const codeMatches = venues.filter((item) => normalized(item.code) === key);
  if (codeMatches.length === 1) return codeMatches[0];
  const nameMatches = venues.filter(
    (item) => normalized(item.name) === key || (item.shortName && normalized(item.shortName) === key),
  );
  if (nameMatches.length === 1) return nameMatches[0];
  if (nameMatches.length > 1) throw new Error(`场馆“${value}”存在重名，请改填场馆编码`);
  throw new Error(`找不到场馆“${value}”，请从模板“可选值”工作表复制名称或编码`);
}

function resolveOwner(value: string, users: ImportUser[]): ImportUser {
  const key = normalized(value);
  const idMatch = users.find((item) => item.id && normalized(item.id) === key);
  if (idMatch) return idMatch;
  const accountMatches = users.filter((item) => item.loginName && normalized(item.loginName) === key);
  if (accountMatches.length === 1) return accountMatches[0];
  const nameMatches = users.filter((item) => item.name && normalized(item.name) === key);
  if (nameMatches.length === 1) return nameMatches[0];
  if (nameMatches.length > 1) throw new Error(`责任人“${value}”存在重名，请改填登录账号`);
  throw new Error(`找不到责任人“${value}”，请填写有效登录账号或唯一姓名`);
}

function setSheetWidths(sheet: XLSX.WorkSheet, widths: number[]) {
  sheet['!cols'] = widths.map((wch) => ({ wch }));
}

async function downloadImportTemplate() {
  const [venues, userResponse] = await Promise.all([loadVenueCatalog(), searchUsers({ keywords: '' })]);
  const users = responseList<ImportUser>(userResponse);
  const selectedVenue = venues.find((item) => item.id === filters.orgId);
  const workbook = XLSX.utils.book_new();
  const rows = [
    {
      所属场馆: selectedVenue?.name || '',
      文档类型: typeLabel(filters.type || props.allowedTypes[0]),
      业务领域: '',
      制度编码: '',
      制度名称: '示例制度名称',
      制度分类: '安全制度',
      适用范围: '本场馆',
      责任人账号: '',
      密级: '内部',
      审核级数: 1,
      正文: '请填写正文',
      变更说明: '首次导入',
    },
  ];
  const importSheet = XLSX.utils.json_to_sheet(rows);
  setSheetWidths(importSheet, [24, 16, 16, 20, 28, 18, 24, 18, 12, 12, 46, 24]);
  XLSX.utils.book_append_sheet(workbook, importSheet, '制度导入');

  const instructions = [
    { 字段: '所属场馆', 必填: '是', 填写说明: '填写场馆名称或场馆编码，不需要查询内部 ID；重名时请填写编码。' },
    { 字段: '文档类型', 必填: '是', 填写说明: '可填写中文名称或编码，以“可选值”工作表为准。' },
    { 字段: '业务领域', 必填: '否', 填写说明: '可填写中文名称或编码，以“可选值”工作表为准。' },
    { 字段: '制度编码', 必填: '否', 填写说明: '留空由系统自动生成。' },
    { 字段: '责任人账号', 必填: '是', 填写说明: '优先填写登录账号，也支持没有重名的人员姓名。' },
    { 字段: '密级', 必填: '否', 填写说明: '留空默认为“内部”，可填写中文名称或编码。' },
    { 字段: '审核级数', 必填: '否', 填写说明: '1 至 10 的整数，留空默认为 1。' },
    { 字段: '制度名称、正文', 必填: '是', 填写说明: '每行一份制度；系统会在提交前逐行校验。' },
  ];
  const instructionSheet = XLSX.utils.json_to_sheet(instructions);
  setSheetWidths(instructionSheet, [18, 10, 72]);
  XLSX.utils.book_append_sheet(workbook, instructionSheet, '填写说明');

  const optionRows = [
    ...venues.map((item) => ({ 分类: '场馆', 名称: item.name, 编码: item.code })),
    ...typeOptions.value.map((item) => ({ 分类: '文档类型', 名称: item.label, 编码: item.value })),
    ...domainOptions.map((item) => ({ 分类: '业务领域', 名称: item.label, 编码: item.value })),
    ...confidentialityOptions.map((item) => ({ 分类: '密级', 名称: item.label, 编码: item.value })),
    ...users
      .filter((item) => item.id && (item.loginName || item.name))
      .map((item) => ({ 分类: '责任人', 名称: item.name || '', 编码: item.loginName || '' })),
  ];
  const optionSheet = XLSX.utils.json_to_sheet(optionRows);
  setSheetWidths(optionSheet, [16, 28, 28]);
  XLSX.utils.book_append_sheet(workbook, optionSheet, '可选值');
  XLSX.writeFile(workbook, `${props.title}导入模板.xlsx`);
}

async function importDocuments(file: File): Promise<ImportResult> {
  error.value = '';
  const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' });
  const sheet = workbook.Sheets[workbook.SheetNames[0]];
  const sourceRows = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, { defval: '' });
  if (!sourceRows.length) throw new Error('导入文件没有数据。');
  if (sourceRows.length > 500) throw new Error('单次最多导入 500 条制度。');

  const venues = await loadVenueCatalog();
  const ownerReferences = [...new Set(sourceRows.map((row) => cell(row, '责任人账号', '责任人')).filter(Boolean))];
  const ownerSearchResults = new Map<string, ImportUser[]>();
  await Promise.all(
    ownerReferences.map(async (reference) => {
      ownerSearchResults.set(reference, responseList<ImportUser>(await searchUsers({ keywords: reference })));
    }),
  );

  const documents: SafetyDocumentCommand[] = [];
  const rowErrors: string[] = [];
  sourceRows.forEach((row, index) => {
    const rowNo = index + 2;
    try {
      const venueReference = cell(row, '所属场馆', '场馆名称', '场馆编码') || filters.orgId || '';
      const ownerReference = cell(row, '责任人账号', '责任人');
      const content = cell(row, '正文', 'content');
      const title = cell(row, '制度名称', '名称', 'title');
      if (!venueReference || !title || !ownerReference || !content) {
        throw new Error('所属场馆、制度名称、责任人账号和正文均为必填项');
      }
      const owner = resolveOwner(ownerReference, ownerSearchResults.get(ownerReference) || []);
      if (!owner.id) throw new Error(`责任人“${ownerReference}”缺少有效用户编号`);
      const approvalsText = cell(row, '审核级数', 'requiredApprovals') || '1';
      const requiredApprovals = Number(approvalsText);
      if (!Number.isInteger(requiredApprovals) || requiredApprovals < 1 || requiredApprovals > 10) {
        throw new Error('审核级数必须是 1 至 10 的整数');
      }
      const typeText = cell(row, '文档类型', 'type') || typeLabel(filters.type || props.allowedTypes[0]);
      const domainText = cell(row, '业务领域', 'domain');
      documents.push({
        orgId: resolveVenue(venueReference, venues).id,
        type: parseImportedType(typeText),
        domain: domainText ? parseImportedOption(domainText, domainOptions, '业务领域') : null,
        code: cell(row, '制度编码', '编码', 'code'),
        title,
        category: cell(row, '制度分类', '分类', 'category') || '未分类',
        applicableScope: cell(row, '适用范围', 'applicableScope') || '本场馆',
        ownerUserId: owner.id,
        confidentiality: parseImportedOption(
          cell(row, '密级', 'confidentiality') || '内部',
          confidentialityOptions,
          '密级',
        ),
        requiredApprovals,
        content,
        changeSummary: cell(row, '变更说明', 'changeSummary') || '批量导入',
        metadata: {},
      });
    } catch (cause: any) {
      rowErrors.push(`第 ${rowNo} 行：${cause?.message || '数据格式不正确'}`);
    }
  });
  if (rowErrors.length) {
    const more = rowErrors.length > 8 ? `；另有 ${rowErrors.length - 8} 行错误` : '';
    throw new Error(`导入校验未通过：${rowErrors.slice(0, 8).join('；')}${more}`);
  }
  await safetyApi.createManagementDocuments(documents);
  return { count: documents.length };
}

function handleImportFinished(result?: ImportResult) {
  error.value = '';
  message.success(`成功导入 ${result?.count || 0} 条文档`);
  refreshTable();
}

function handleImportError(cause: any) {
  error.value = cause?.message || '制度导入失败，请核对文件后重试。';
}
async function exportDocuments() {
  exporting.value = true;
  error.value = '';
  try {
    const [documentResponse, venues, userResponse] = await Promise.all([
      safetyApi.managementDocuments({
        orgId: filters.orgId || undefined,
        type: filters.type || undefined,
        status: filters.status || undefined,
      }),
      loadVenueCatalog(),
      searchUsers({ keywords: '' }),
    ]);
    const documents = unwrapResponse<SafetyDocumentVo[]>(documentResponse) || [];
    const venueNames = new Map(venues.map((item) => [item.id, item.name]));
    const ownerAccounts = new Map(
      responseList<ImportUser>(userResponse)
        .filter((item) => item.id)
        .map((item) => [item.id as string, item.loginName || item.name || item.id || '']),
    );
    const values = documents
      .filter((item) => props.allowedTypes.includes(item.type))
      .map((row) => ({
        所属场馆: venueNames.get(row.orgId) || row.orgId,
        文档类型: typeLabel(row.type),
        制度编码: row.code,
        制度名称: row.title,
        制度分类: row.category,
        适用范围: row.applicableScope,
        责任人账号: ownerAccounts.get(row.ownerUserId) || row.ownerUserId,
        密级: confidentialityOptions.find((item) => item.value === row.confidentiality)?.label || row.confidentiality,
        审核级数: row.requiredApprovals,
        状态: statusLabel(row.status),
        当前版本: row.latestVersion.versionNo,
        正文: row.latestVersion.content,
        变更说明: row.latestVersion.changeSummary,
      }));
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(values), '安全文档');
    XLSX.writeFile(workbook, `${props.title}.xlsx`);
  } catch (cause: any) {
    error.value = cause?.message || `${props.title}导出失败。`;
  } finally {
    exporting.value = false;
  }
}

async function fetchDocuments(params: Record<string, unknown>) {
  error.value = '';
  try {
    return await safetyApi.managementDocumentPage({ ...params, types: props.allowedTypes.join(',') });
  } catch (cause: any) {
    error.value = cause?.message || `${props.title}加载失败。`;
    throw cause;
  }
}

function refreshTable() {
  tableRef.value?.handleRefresh?.();
}
async function showDetail(row: SafetyDocumentVo) {
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = unwrapResponse<SafetyDocumentVo>(await safetyApi.managementDocument(row.id));
  } catch (cause: any) {
    error.value = cause?.message || '文档详情加载失败。';
  } finally {
    detailLoading.value = false;
  }
}
async function saveDocument() {
  if (editMode.value === 'create' && (!documentForm.orgId || !documentForm.title || !documentForm.ownerUserId)) {
    error.value = '请完整填写场馆、名称和责任人。';
    return;
  }
  if (!versionForm.content || !versionForm.changeSummary) {
    error.value = '请填写正文和变更说明。';
    return;
  }
  submitting.value = true;
  try {
    const version = {
      ...versionForm,
      attachments: attachmentRefs.value,
      complianceBasis: parseBasis(),
      metadata: editMode.value === 'version' ? detail.value?.latestVersion.metadata || {} : {},
    };
    if (editMode.value === 'create') {
      await safetyApi.createManagementDocument({ ...documentForm, ...version } as SafetyDocumentCommand);
    } else if (detail.value) {
      await safetyApi.createManagementDocumentVersion(detail.value.id, {
        expectedCurrentRevision: detail.value.currentRevision,
        ...version,
      } as SafetyDocumentVersionCommand);
    }
    message.success('保存成功');
    editVisible.value = false;
    refreshTable();
    if (detailVisible.value && detail.value) await showDetail(detail.value);
  } catch (cause: any) {
    error.value = cause?.message || '保存失败。';
  } finally {
    submitting.value = false;
  }
}
async function submitVersion(row: SafetyDocumentVo) {
  try {
    await safetyApi.submitManagementDocumentVersion(row.latestVersion.id);
    message.success('已提交审核');
    refreshTable();
    await showDetail(row);
  } catch (cause: any) {
    error.value = cause?.message || '提交审核失败。';
  }
}
async function reviewVersion() {
  if (!detail.value || !reviewForm.opinion) return;
  submitting.value = true;
  try {
    await safetyApi.reviewManagementDocumentVersion(detail.value.latestVersion.id, {
      ...reviewForm,
      annotations: parseList(annotationText.value, /[;；]/),
    });
    message.success('审核意见已提交');
    reviewVisible.value = false;
    refreshTable();
    await showDetail(detail.value);
  } catch (cause: any) {
    error.value = cause?.message || '审核失败。';
  } finally {
    submitting.value = false;
  }
}
async function saveAccess() {
  if (!detail.value) return;
  if (accessRules.value.some((rule) => !rule.subjectId.trim() || !rule.actions.length)) {
    error.value = '每条权限规则都必须填写对象 ID 并至少选择一个授权项。';
    return;
  }
  submitting.value = true;
  try {
    await safetyApi.replaceManagementDocumentAccess(detail.value.id, { rules: accessRules.value });
    message.success('权限配置已保存');
    accessVisible.value = false;
  } catch (cause: any) {
    error.value = cause?.message || '权限保存失败。';
  } finally {
    submitting.value = false;
  }
}
async function saveScopeAccess() {
  if (!scopeAccessForm.orgId || !scopeAccessForm.scopeValue.trim()) {
    error.value = '请选择场馆并填写权限范围值。';
    return;
  }
  if (scopeAccessRules.value.some((rule) => !rule.subjectId.trim() || !rule.actions.length)) {
    error.value = '每条权限规则都必须填写对象 ID 并至少选择一个授权项。';
    return;
  }
  submitting.value = true;
  try {
    await safetyApi.replaceManagementDocumentAccessPolicy({
      ...scopeAccessForm,
      rules: scopeAccessRules.value,
    });
    message.success('类型/目录权限配置已保存');
    scopeAccessVisible.value = false;
  } catch (cause: any) {
    error.value = cause?.message || '类型/目录权限保存失败。';
  } finally {
    submitting.value = false;
  }
}
async function downloadDocument(row: SafetyDocumentVo) {
  try {
    const response: any = await safetyApi.downloadManagementDocument(row.id);
    const blob =
      response instanceof Blob
        ? response
        : response?.data instanceof Blob
          ? response.data
          : new Blob([response], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${row.code}-${row.latestVersion.versionNo}.md`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (cause: any) {
    error.value = cause?.message || '文档下载失败。';
  }
}
async function archive(row: SafetyDocumentVo) {
  try {
    await safetyApi.archiveManagementDocument(row.id);
    message.success('已归档');
    refreshTable();
  } catch (cause: any) {
    error.value = cause?.message || '归档失败。';
  }
}
</script>

<style scoped>
.document-manager {
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
.safety-page-table {
  min-height: 0;
  flex: 1;
}
.state-alert {
  margin-bottom: 14px;
}
.dialog-card {
  width: min(820px, calc(100vw - 32px));
}
.small-dialog {
  width: min(560px, calc(100vw - 32px));
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.detail-actions {
  margin: 16px 0;
}
.document-content {
  white-space: pre-wrap;
  line-height: 1.7;
}
.muted {
  color: var(--text-color-3);
}
.review-annotations {
  margin-top: 4px;
  color: var(--text-color-3);
}
.access-rule {
  display: grid;
  grid-template-columns: 110px minmax(140px, 1fr) minmax(220px, 1.5fr) auto;
  gap: 8px;
  margin-bottom: 10px;
}
.form-actions {
  margin-top: 16px;
}
@media (max-width: 720px) {
  .document-manager {
    padding: 8px;
  }
  .access-rule {
    grid-template-columns: 1fr;
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
