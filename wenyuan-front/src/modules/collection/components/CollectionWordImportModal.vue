<template>
  <NModal
    v-model:show="visible"
    preset="card"
    :title="modalTitle"
    style="width: 900px; max-width: calc(100vw - 32px)"
    :mask-closable="!loading"
  >
    <NAlert type="info" :bordered="false" style="margin-bottom: 14px">
      导入前将对文档内容进行校验，校验通过后方可提交。导入成功后，原始文件将自动归档至藏品附件。
    </NAlert>

    <NForm v-if="expectedDocumentType === 'REGISTRATION'" label-placement="left" label-width="92">
      <NFormItem label="导入模式">
        <NRadioGroup v-model:value="mode">
          <NRadioButton value="CREATE_ONLY">仅新增</NRadioButton>
          <NRadioButton value="UPSERT">新增并更正</NRadioButton>
        </NRadioGroup>
      </NFormItem>
      <NFormItem v-if="mode === 'UPSERT'" label="更正原因" required>
        <NInput
          v-model:value="reason"
          type="textarea"
          :rows="2"
          placeholder="已有总登记号更新时写入总账更正依据"
        />
      </NFormItem>
    </NForm>

    <div class="word-file-row">
      <input ref="fileInput" type="file" accept=".doc,.docx" hidden @change="onFileChange" />
      <NButton type="primary" secondary :disabled="loading" @click="fileInput?.click()">选择 Word</NButton>
      <div>
        <strong>{{ file?.name || '未选择文件' }}</strong>
        <p>{{ file ? '已选择，可重新预校验或确认导入' : '仅支持 .doc / .docx，最大 20MB' }}</p>
      </div>
      <NButton :loading="validating" :disabled="!file || loading" @click="validate">预校验</NButton>
    </div>

    <template v-if="preview">
      <NAlert
        :type="preview.valid ? 'success' : 'error'"
        :title="preview.valid ? '结构和业务校验通过' : '文档不能导入'"
        style="margin-top: 14px"
      >
        {{ preview.valid ? documentTypeText(preview.documentType) : '请根据下方错误更换或修正文档。' }}
      </NAlert>

      <NDataTable
        v-if="preview.errors?.length"
        :scroll-x="570"
        :columns="errorColumns"
        :data="preview.errors"
        size="small"
        :bordered="false"
        :max-height="150"
        style="margin-top: 12px"
      />
      <NAlert v-if="preview.warnings?.length" type="warning" title="导入提示" style="margin-top: 12px">
        <ul class="compact-list">
          <li v-for="warning in preview.warnings" :key="warning">{{ warning }}</li>
        </ul>
      </NAlert>

      <NDescriptions
        v-if="preview.valid"
        label-placement="left"
        :column="2"
        bordered
        size="small"
        style="margin-top: 12px"
      >
        <NDescriptionsItem label="识别类型">{{ documentTypeText(preview.documentType) }}</NDescriptionsItem>
        <NDescriptionsItem label="目标馆">{{ preview.confirmation?.orgName }}（{{ preview.confirmation?.orgId }}）</NDescriptionsItem>
        <NDescriptionsItem label="总登记号">{{ preview.totalRegNo }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品名称">{{ preview.collectionName }}</NDescriptionsItem>
        <NDescriptionsItem v-if="preview.registration" label="类别">{{ preview.catalog?.comprehensiveCategory || preview.registration.categoryId }}</NDescriptionsItem>
        <NDescriptionsItem v-if="preview.registration" label="级别">{{ preview.registration.level }}</NDescriptionsItem>
        <NDescriptionsItem v-if="preview.registration" label="年代">{{ preview.registration.era }}</NDescriptionsItem>
        <NDescriptionsItem v-if="preview.registration" label="质地">{{ preview.registration.texture }}</NDescriptionsItem>
        <NDescriptionsItem v-if="preview.startYear || preview.endYear" label="跟踪年度" :span="2">
          {{ preview.startYear || '--' }} 至 {{ preview.endYear || '--' }}
        </NDescriptionsItem>
      </NDescriptions>

      <section v-if="preview.events?.length" class="preview-section">
        <h4>管理事项（{{ preview.events.length }}）</h4>
        <NDataTable :columns="eventColumns" :data="preview.events" size="small" :bordered="false" :max-height="220" :scroll-x="930" />
      </section>
      <section v-if="preview.movements?.length" class="preview-section">
        <h4>历史出入库（{{ preview.movements.length }}）</h4>
        <NDataTable
          :scroll-x="1020"
          :columns="movementColumns"
          :data="preview.movements"
          size="small"
          :bordered="false"
          :max-height="220"
        />
      </section>
    </template>

    <template #footer>
      <NSpace justify="end">
        <NButton :disabled="loading" @click="visible = false">取消</NButton>
        <NButton :loading="validating" :disabled="!file || loading" @click="validate">预校验</NButton>
        <NButton type="primary" :loading="committing" :disabled="!canCommit" @click="commit">确认导入</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { collectionApi } from '../api/collection';
import { useCollectionImportSession } from '../composables/useCollectionImportSession';
import { wordValidation, wordCommit } from '../utils/importResponses';
import { REGISTRATION_RECEIPT_HINT, wordImportCreatedRegistration } from '../utils/accessionReceiptHints';
import type {
  CollectionImportMode,
  CollectionWordDocumentType,
  CollectionWordImportPreviewVo,
} from '../typings/collection';
import { formatDateTime } from '@/utils';

const emit = defineEmits<{ committed: [collectionId?: string] }>();
const message = useMessage();
const visible = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);
const file = ref<File | null>(null);
const preview = ref<CollectionWordImportPreviewVo | null>(null);
const mode = ref<CollectionImportMode>('CREATE_ONLY');
const reason = ref('');
const expectedCollectionId = ref<string>();
const expectedDocumentType = ref<CollectionWordDocumentType>('REGISTRATION');
const confirmed = ref<{ file: File; params: ReturnType<typeof params>; preview: CollectionWordImportPreviewVo } | null>(null);
const session = useCollectionImportSession(() => { visible.value = false; clearInput(); });
const loading = session.busy;
const validating = computed(() => session.operation.value === 'validate');
const committing = computed(() => session.operation.value === 'commit');
const modalTitle = computed(() => expectedDocumentType.value === 'REGISTRATION' ? 'Word 导入藏品登记表' : '导入动态跟踪记录汇总表');
const canCommit = computed(() => !!confirmed.value && !!preview.value?.valid && !loading.value);
function clearInput() {
  file.value = null; preview.value = null; confirmed.value = null;
  if (fileInput.value) fileInput.value.value = '';
}
watch([file, mode, reason, expectedCollectionId, expectedDocumentType], () => {
  session.invalidate(); preview.value = null; confirmed.value = null;
}, { flush: 'sync' });
watch(visible, (show) => { if (!show) { session.invalidate(); clearInput(); reason.value = ''; expectedCollectionId.value = undefined; } }, { flush: 'sync' });

const errorColumns = [
  { title: '位置', key: 'field', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width: 150 },
  { title: '错误', key: 'message', width: 420, resizable: true, ellipsis: { tooltip: true }, minWidth: 360 },
];
const eventColumns = [
  { title: '类型', key: 'eventType', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width: 150, render: (row: any) => eventTypeText(row.eventType) },
  { title: '栏目', key: 'title', minWidth: 120, resizable: true, ellipsis: { tooltip: true }, width: 180 },
  { title: '内容', key: 'content', width: 420, resizable: true, minWidth: 360, ellipsis: { tooltip: true } },
  { title: '时间', key: 'occurredAt', minWidth: 160, resizable: true, ellipsis: { tooltip: true }, width: 180, render: (row: any) => dateText(row.occurredAt) },
];
const movementColumns = [
  { title: '时间', key: 'operatedAt', minWidth: 160, resizable: true, ellipsis: { tooltip: true }, width: 180, render: (row: any) => dateText(row.operatedAt) },
  { title: '类型', key: 'type', minWidth: 65, resizable: true, ellipsis: { tooltip: true }, width: 80, render: (row: any) => (row.type === 'OUT' ? '出库' : '入库') },
  { title: '出库位', key: 'fromWarehouseName', minWidth: 120, resizable: true, ellipsis: { tooltip: true }, width: 160 },
  { title: '入库位', key: 'toWarehouseName', minWidth: 120, resizable: true, ellipsis: { tooltip: true }, width: 160 },
  { title: '事由', key: 'basis', width: 300, resizable: true, minWidth: 220, ellipsis: { tooltip: true } },
  { title: '经手人', key: 'operator', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width: 140 },
];

function open(options: { expectedCollectionId?: string; expectedDocumentType: CollectionWordDocumentType }) {
  session.invalidate(); clearInput();
  expectedCollectionId.value = options.expectedCollectionId;
  expectedDocumentType.value = options.expectedDocumentType;
  mode.value = 'CREATE_ONLY'; reason.value = ''; visible.value = true;
}
function close() { visible.value = false; session.invalidate(); clearInput(); }
function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const selected = input.files?.[0]; if (!selected || !visible.value) return;
  file.value = selected; input.value = ''; void validate();
}
function params() {
  return { mode: mode.value, reason: reason.value.trim() || undefined,
    expectedCollectionId: expectedCollectionId.value, expectedDocumentType: expectedDocumentType.value };
}
async function validate() {
  const selected = file.value; if (!selected || !visible.value || loading.value) return;
  const options = params();
  if (options.mode === 'UPSERT' && !options.reason) { message.warning('更正已有藏品时必须填写更正原因'); return; }
  preview.value = null; confirmed.value = null;
  const ticket = session.begin('validate'); if (ticket === null) return;
  try {
    const response = await collectionApi.validateWordImport(selected, options);
    if (!session.current(ticket) || !visible.value) return;
    const value = wordValidation(response, options.expectedDocumentType, options.expectedCollectionId);
    preview.value = value;
    if (value.valid) { confirmed.value = { file: selected, params: options, preview: value }; message.success('预校验通过，请核对目标馆和文档内容后确认'); }
    else message.warning('Word 预校验未通过，未导入');
  } catch (error: any) {
    if (session.current(ticket)) { preview.value = null; confirmed.value = null; message.error(error?.message || 'Word 预校验失败'); }
  } finally { session.finish(ticket); }
}
async function commit() {
  const value = confirmed.value;
  if (!value?.preview.confirmation || !visible.value || !canCommit.value) return;
  const ticket = session.begin('commit'); if (ticket === null) return;
  const confirmation = value.preview.confirmation;
  try {
    const response = await collectionApi.commitWordImport(value.file, { ...value.params,
      expectedOrgId: confirmation.orgId, expectedValidationHash: confirmation.validationHash });
    if (!session.current(ticket) || !visible.value) return;
    const data = wordCommit(response, value.preview);
    if (!data.valid) {
      preview.value = { ...value.preview, valid: false, errors: data.errors }; confirmed.value = null;
      message.warning('重新校验未通过，未写入任何数据'); return;
    }
    message.success('Word 导入完成，管理记录 ' + data.eventCount + ' 条、历史出入库记录 ' + data.movementCount + ' 条');
    if (wordImportCreatedRegistration(expectedDocumentType.value, data)) message.info(REGISTRATION_RECEIPT_HINT, { duration: 6000 });
    close(); emit('committed', data.collectionId);
  } catch (error: any) {
    if (session.current(ticket)) { preview.value = null; confirmed.value = null; message.error(error?.message || 'Word 提交失败，请核对处理结果'); }
  } finally { session.finish(ticket); }
}

function documentTypeText(type?: CollectionWordDocumentType) {
  return type === 'REGISTRATION' ? '文物藏品登记表' : type === 'DYNAMIC_TRACKING' ? '文物藏品动态跟踪记录汇总表' : '--';
}

function eventTypeText(type?: string) {
  return (
    {
      EXTRACT_RETURN: '提取退还',
      OBSERVATION: '观察监测',
      RESTORATION: '保护修复',
      DIGITIZATION: '数字化',
      APPRAISAL_CHANGE: '鉴定变更',
      RESEARCH_USE: '研究利用',
      INCIDENT: '事故',
      DEREGISTRATION: '退出馆藏记录',
      NOTE: '备注',
      REGISTRATION_DETAIL: '登记扩展',
    } as Record<string, string>
  )[type || ''] || type || '--';
}

function dateText(value?: string) {
  return value ? formatDateTime(value) : '--';
}

defineExpose({ open, close });
</script>

<style scoped>
.word-file-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
}

.word-file-row p {
  margin: 3px 0 0;
  color: var(--text-color-3, #6b7280);
}

.preview-section {
  margin-top: 14px;
}

.preview-section h4 {
  margin: 0 0 8px;
}

.compact-list {
  margin: 0;
  padding-left: 20px;
}

@media (max-width: 640px) {
  .word-file-row {
    grid-template-columns: 1fr;
  }
}
</style>
