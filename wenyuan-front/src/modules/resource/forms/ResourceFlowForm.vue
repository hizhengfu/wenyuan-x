<template>
  <div class="resource-flow-form">
    <n-descriptions bordered :column="2" size="small">
      <n-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">
        {{ displayValue(field) }}
      </n-descriptions-item>
    </n-descriptions>

    <template v-if="isResourceImport && packageAuditSummary">
      <n-divider title-placement="left">资源包审核摘要</n-divider>
      <n-alert :type="validationAlertType" :show-icon="true" class="mb-3">
        {{ packageAuditSummary.packageTypeLabel || '复合资源包' }}：{{ validationStatusLabel }}。
        包内文件作为一个逻辑资源整体审核，不需要逐文件审批。
      </n-alert>
      <n-descriptions bordered :column="2" size="small">
        <n-descriptions-item label="资源包类型">
          {{ packageAuditSummary.packageTypeLabel || packageAuditSummary.packageType || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="主文件">
          <span class="break-all">{{ packageAuditSummary.mainRelativePath || '-' }}</span>
        </n-descriptions-item>
        <n-descriptions-item label="文件总数">
          {{ packageAuditSummary.fileCount || 0 }} 个
        </n-descriptions-item>
        <n-descriptions-item label="资源包大小">
          {{ formatSize(Number(packageAuditSummary.totalSize || 0)) }}
        </n-descriptions-item>
        <n-descriptions-item label="文件格式" :span="2">
          <n-space size="small">
            <n-tag v-for="item in extensionEntries" :key="item.name" size="small" :bordered="false">
              {{ item.name }} × {{ item.count }}
            </n-tag>
          </n-space>
        </n-descriptions-item>
      </n-descriptions>

      <template v-if="validationIssues.length">
        <n-divider title-placement="left">自动校验问题</n-divider>
        <n-alert
          v-for="(issue, index) in visibleValidationIssues"
          :key="`${issue.field || 'package'}-${index}`"
          :type="issue.severity === 'FAILED' ? 'error' : 'warning'"
          :show-icon="false"
          class="audit-issue"
        >
          <div class="audit-issue-path">{{ issue.field || '资源包' }}</div>
          <div>{{ issue.message || issue }}</div>
        </n-alert>
        <div v-if="validationIssues.length > visibleValidationIssues.length" class="issue-more">
          还有 {{ validationIssues.length - visibleValidationIssues.length }} 条问题，请打开资源详情查看完整列表。
        </div>
      </template>
    </template>

    <n-space v-if="isResourceImport && resourceId" justify="end" class="preview-action">
      <n-button type="primary" secondary @click="previewShow = true">查看资源整体预览与包内文件</n-button>
    </n-space>

    <ResourcePreview v-model:show="previewShow" :resource-id="resourceId" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { get as getResource, RESOURCE_PERMISSION_LEVEL_OPTIONS, RESOURCE_TYPE_OPTIONS } from '../api/resource';
import { useFlowForm } from '../../flow/composables/useFlowForm';
import type { FlowFormExpose, FlowFormProps } from '../../flow/typings/flow';
import ResourcePreview from '../components/ResourcePreview.vue';

type Field = {
  key: string;
  label: string;
  options?: Array<{ label: string; value: string }>;
};
type ValidationStatus = 'PASS' | 'MANUAL_REVIEW' | 'FAILED';
const VALIDATION_ALERT_TYPE: Record<ValidationStatus, 'success' | 'warning' | 'error'> = {
  PASS: 'success',
  MANUAL_REVIEW: 'warning',
  FAILED: 'error',
};

const props = defineProps<FlowFormProps>();
const emit = defineEmits<{ (e: 'update:formData', val: Record<string, any>): void }>();
const { inner, validate } = useFlowForm(props, emit);
const previewShow = ref(false);

const businessType = computed(() => props.businessType || props.task?.businessType || '');
const resourceId = computed(() => props.businessKey || props.task?.businessKey || inner.value.id || '');
const isResourceImport = computed(() => businessType.value.toUpperCase() === 'RESOURCE_IMPORT');
const packageAuditSummary = computed<any>(() => inner.value.packageAuditSummary || null);

const validationIssues = computed<any[]>(() => {
  const summaryIssues = packageAuditSummary.value?.issues;
  if (Array.isArray(summaryIssues)) return summaryIssues;
  const raw = inner.value.validationIssues;
  if (Array.isArray(raw)) return raw;
  if (typeof raw !== 'string' || !raw.trim()) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [{ field: '资源包', message: raw, severity: 'MANUAL_REVIEW' }];
  }
});
const visibleValidationIssues = computed(() => validationIssues.value.slice(0, 10));
const extensionEntries = computed(() =>
  Object.entries(packageAuditSummary.value?.extensionCounts || {})
    .map(([name, count]) => ({ name, count: Number(count || 0) }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 12),
);
const validationStatus = computed<ValidationStatus>(() => {
  const value = String(packageAuditSummary.value?.status || inner.value.validationStatus || 'MANUAL_REVIEW');
  return ['PASS', 'MANUAL_REVIEW', 'FAILED'].includes(value) ? value as ValidationStatus : 'MANUAL_REVIEW';
});
const validationStatusLabel = computed(() => ({
  PASS: '自动校验通过',
  MANUAL_REVIEW: '需要人工复核',
  FAILED: '自动校验不通过',
}[validationStatus.value] || validationStatus.value));
const validationAlertType = computed(() => VALIDATION_ALERT_TYPE[validationStatus.value]);

const fields = computed<Field[]>(() => {
  const type = businessType.value.toUpperCase();
  if (type === 'RESOURCE_ACCESS_GRANT') {
    return [
      { key: 'permissionLevel', label: '权限等级', options: RESOURCE_PERMISSION_LEVEL_OPTIONS },
      { key: 'resourceType', label: '资源类型', options: RESOURCE_TYPE_OPTIONS },
      { key: 'culturalRelicNo', label: '文物编号' },
      { key: 'operationTypes', label: '操作类型' },
      { key: 'usageScene', label: '使用场景' },
    ];
  }
  return [
    { key: 'name', label: '资源名称' },
    { key: 'code', label: '资源编号' },
    { key: 'resourceType', label: '资源类型', options: RESOURCE_TYPE_OPTIONS },
    { key: 'collectionTime', label: '采集时间' },
    { key: 'validationStatus', label: '校验状态' },
    { key: 'permissionLevel', label: '权限等级', options: RESOURCE_PERMISSION_LEVEL_OPTIONS },
    { key: 'culturalRelicNo', label: '文物编号' },
  ];
});

watch(
  () => [businessType.value, resourceId.value] as const,
  async ([businessType, businessKey]) => {
    if (businessType?.toUpperCase?.() !== 'RESOURCE_IMPORT' || !businessKey) return;
    const resp: any = await getResource(businessKey);
    const data = resp?.data?.data ?? resp?.data;
    if (data) inner.value = { ...inner.value, ...data };
  },
  { immediate: true },
);

function displayValue(field: Field) {
  const value = inner.value[field.key];
  if (value === undefined || value === null || value === '') return '-';
  return field.options?.find((item) => item.value === value)?.label || String(value);
}

function formatSize(size: number) {
  if (!size) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  return `${(size / 1024 ** index).toFixed(index === 0 ? 0 : 2)} ${units[index]}`;
}

defineExpose<FlowFormExpose>({ validate });
</script>

<style scoped>
.resource-flow-form {
  min-width: 0;
}

.audit-issue + .audit-issue {
  margin-top: 8px;
}

.audit-issue-path {
  margin-bottom: 2px;
  color: var(--n-title-text-color);
  font-size: 12px;
  font-weight: 600;
  word-break: break-all;
}

.issue-more {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
}

.preview-action {
  margin-top: 16px;
}
</style>
