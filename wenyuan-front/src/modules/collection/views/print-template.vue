<template>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    class="print-template-page"
    :columns="columns"
    :fetch-data="fetchTemplates"
    :pagination="false"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :is-export="false"
    :show-operate="false"
    row-key-field="code"
  >
    <template #header>
      <div>
        <div class="page-title">打印模板</div>
        <p>维护各业务表单的 Word 模板、标题、签字栏，并查看模板字段占位符。</p>
      </div>
    </template>
    <template #toolbar-extra>
      <NTag type="info" :bordered="false">{{ templates.length }} 种表单</NTag>
      <NButton secondary :loading="loading" @click="refreshTable">刷新</NButton>
    </template>

    <NModal v-model:show="editVisible" preset="card" :style="{ width: '720px', maxWidth: '92vw' }" :title="editTitle">
      <NForm ref="formRef" :model="form" :rules="formRules" label-placement="top" size="small">
        <NGrid :cols="2" :x-gap="16">
          <NFormItemGi label="模板名称" path="name">
            <NInput v-model:value="form.name" placeholder="如：征集入藏审批登记表" />
          </NFormItemGi>
          <NFormItemGi label="表单标题">
            <NInput v-model:value="form.title" placeholder="打印文件中的主标题" />
          </NFormItemGi>
          <NFormItemGi label="单号字段名">
            <NInput v-model:value="form.documentLabel" placeholder="如：征集申请号" />
          </NFormItemGi>
          <NFormItemGi label="藏品清单标题">
            <NInput v-model:value="form.collectionTitle" placeholder="如：入藏藏品信息" />
          </NFormItemGi>
          <NFormItemGi label="启用">
            <NSwitch v-model:value="form.enabled" />
          </NFormItemGi>
        </NGrid>
        <NFormItem label="Word 模板">
          <HUploadInput
            v-model:value="form.templatePath"
            module="collection_print_template"
            accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            placeholder="上传 .docx 模板；留空则使用系统默认模板"
            clearable
          />
        </NFormItem>
        <NFormItem label="签字栏">
          <NInput v-model:value="signatureText" placeholder="多个签字栏用逗号分隔，如：申请人，审核人，批准人，日期" />
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="form.remark" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="模板适用说明" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="space-between">
          <NButton :disabled="!editing?.custom" secondary @click="resetCurrent">恢复默认</NButton>
          <NSpace>
            <NButton @click="editVisible = false">取消</NButton>
            <NButton type="primary" :loading="saving" @click="saveCurrent">保存模板</NButton>
          </NSpace>
        </NSpace>
      </template>
    </NModal>

    <HPageForm v-model:show="usageVisible" :title="usageTitle" content-only :enable-cancel="false">
        <div class="usage-tip">
          在 Word 模板中直接填写占位符即可。藏品明细字段需要放在表格行里，打印时会按藏品数量自动复制该行。附件清单会自动生成，图片显示等比缩略图，其他文件显示名称和存储路径。
        </div>
        <NDataTable :scroll-x="980"
          :columns="usageColumns"
          :data="usageFields"
          :loading="usageLoading"
          :bordered="false"
          size="small"
          :pagination="{ pageSize: 30 }"
          :row-key="usageRowKey"
        />
    </HPageForm>
  </HPageDataTable>
</template>

<script setup lang="ts">
import { validateFormSafely } from '../utils/formValidation';
import { computed, h, reactive, ref } from 'vue';
import type { DataTableColumns, FormInst, FormRules } from 'naive-ui';
import { NButton, NSpace, NTag, useDialog, useMessage } from 'naive-ui';
import { collectionPrintApi } from '../api/print';
import { dictText } from '../utils/dictRender';
import type {
  CollectionPrintFieldUsageVo,
  CollectionPrintTemplateSaveVo,
  CollectionPrintTemplateVo,
} from '../typings/collection';

const message = useMessage();
const dialog = useDialog();
const loading = ref(false);
const saving = ref(false);
const editVisible = ref(false);
const usageVisible = ref(false);
const usageLoading = ref(false);
const templates = ref<CollectionPrintTemplateVo[]>([]);
const usageFields = ref<CollectionPrintFieldUsageVo[]>([]);
const editing = ref<CollectionPrintTemplateVo | null>(null);
const signatureText = ref('');
const tableRef = ref<any>(null);
const formRef = ref<FormInst | null>(null);

const form = reactive<CollectionPrintTemplateSaveVo>({
  name: '',
  title: '',
  documentLabel: '',
  collectionTitle: '',
  templatePath: '',
  signatures: [],
  enabled: true,
  sort: 0,
  remark: '',
});
const formRules: FormRules = {
  name: { required: true, message: '请输入模板名称', trigger: ['blur', 'input'] },
};

const usageRowKey = (row: CollectionPrintFieldUsageVo) => `${row.group}-${row.placeholder}`;
const editTitle = computed(() => (editing.value ? `维护：${editing.value.name}` : '维护打印模板'));
const usageTitle = computed(() => (editing.value ? `字段用法：${editing.value.name}` : '字段用法'));

const columns: DataTableColumns<CollectionPrintTemplateVo> = [
  { ellipsis: { tooltip: true }, resizable: true,
    title: '模板名称',
    key: 'name',
    width: 250,
    fixed: 'left',
    render: (row) => h('div', { class: 'name-cell' }, [
      h('span', row.name),
      h(NTag, { size: 'small', type: row.custom ? 'success' : 'default', bordered: false }, () => (row.custom ? '已维护' : '默认')),
    ]),
  },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '表单类型',
    key: 'type',
    width: 150,
    render: (row) => dictText('col_print_type', row.type),
  },
  { resizable: true, width: 220, title: '表单标题', key: 'title', minWidth: 220, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '单号字段', key: 'documentLabel', width: 130 },
  { ellipsis: { tooltip: true }, resizable: true, title: '藏品清单', key: 'collectionTitle', width: 150 },
  { resizable: true, width: 240,
    title: '签字栏',
    key: 'signatures',
    minWidth: 240,
    render: (row) =>
      h('div', { class: 'signature-tags' }, (row.signatures || []).map((item) =>
        h(NTag, { size: 'small', bordered: false }, () => item),
      )),
  },
  { resizable: true, width: 260,
    title: 'Word 模板',
    key: 'templatePath',
    minWidth: 260,
    ellipsis: { tooltip: true },
    render: (row) => (row.custom ? row.templatePath || '未上传' : '系统默认模板'),
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: (row) =>
      h(NTag, { size: 'small', type: row.enabled === false ? 'default' : 'success', bordered: false }, () =>
        row.enabled === false ? '停用' : '启用',
      ),
  },
  {
    title: '操作',
    key: 'operate',
    type: 'operate' as any,
    width: 190,
    fixed: 'right',
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        h(NButton, { size: 'small', secondary: true, onClick: () => openUsage(row) }, () => '字段'),
        h(NButton, { size: 'small', type: 'primary', secondary: true, onClick: () => openEdit(row) }, () => '维护'),
      ]),
  },
];

const usageColumns: DataTableColumns<CollectionPrintFieldUsageVo> = [
  { ellipsis: { tooltip: true }, resizable: true, title: '分组', key: 'group', width: 100 },
  { ellipsis: { tooltip: true }, resizable: true, title: '字段', key: 'label', width: 120 },
  { resizable: true,
    title: '占位符',
    key: 'placeholder',
    width: 340,
    render: (row) =>
      h(NSpace, { align: 'center', size: 6 }, () => [
        h('code', { class: 'placeholder-code' }, row.placeholder),
        h(NButton, { text: true, type: 'primary', size: 'tiny', onClick: () => copyPlaceholder(row.placeholder) }, () => '复制'),
      ]),
  },
  { resizable: true, title: '示例', key: 'example', width: 160, ellipsis: { tooltip: true } },
  { resizable: true, width: 260, title: '用法', key: 'usage', minWidth: 260, ellipsis: { tooltip: true } },
];

async function fetchTemplates() {
  loading.value = true;
  try {
    const resp = await collectionPrintApi.templates();
    templates.value = resp?.data || [];
    return resp;
  } catch (error: any) {
    message.error(error?.message || '打印模板加载失败');
    throw error;
  } finally {
    loading.value = false;
  }
}

function refreshTable() {
  tableRef.value?.handleRefresh?.();
}

function openEdit(row: CollectionPrintTemplateVo) {
  editing.value = row;
  form.name = row.name;
  form.title = row.title;
  form.documentLabel = row.documentLabel;
  form.collectionTitle = row.collectionTitle;
  form.templatePath = row.custom ? row.templatePath : '';
  form.enabled = row.enabled !== false;
  form.sort = row.sort;
  form.remark = row.remark || '';
  signatureText.value = (row.signatures || []).join('，');
  editVisible.value = true;
}

async function openUsage(row: CollectionPrintTemplateVo) {
  editing.value = row;
  usageVisible.value = true;
  usageLoading.value = true;
  try {
    const resp = await collectionPrintApi.templateFields(row.type);
    usageFields.value = resp?.data?.fields || [];
  } catch (error: any) {
    message.error(error?.message || '字段用法加载失败');
  } finally {
    usageLoading.value = false;
  }
}

async function saveCurrent() {
  if (!editing.value) return;
  if (!(await validateFormSafely(formRef.value))) return;
  saving.value = true;
  try {
    await collectionPrintApi.saveTemplate(editing.value.type, {
      ...form,
      signatures: parseSignatures(signatureText.value),
    });
    message.success('打印模板已保存');
    editVisible.value = false;
    refreshTable();
  } catch (error: any) {
    message.error(error?.message || '打印模板保存失败');
  } finally {
    saving.value = false;
  }
}

function resetCurrent() {
  if (!editing.value) return;
  dialog.warning({
    title: '恢复默认模板',
    content: `确认恢复“${editing.value.name}”为系统默认模板？`,
    positiveText: '恢复默认',
    negativeText: '取消',
    onPositiveClick: async () => {
      await collectionPrintApi.resetTemplate(editing.value!.type);
      message.success('已恢复默认模板');
      editVisible.value = false;
      refreshTable();
    },
  });
}

async function copyPlaceholder(text: string) {
  await navigator.clipboard?.writeText(text);
  message.success('占位符已复制');
}

function parseSignatures(text: string): string[] {
  return text
    .split(/[,，;；\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}
</script>

<style scoped>
.print-template-page {
  padding: 16px;
}

.page-title {
  margin: 4px 0 4px;
  font-size: 24px;
  line-height: 32px;
  font-weight: 700;
  color: #172033;
}

.print-template-page p {
  margin: 0;
  color: #667085;
}

.eyebrow {
  font-size: 13px;
  color: #007c75;
  font-weight: 600;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.signature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.full-input {
  width: 100%;
}

.usage-tip {
  margin-bottom: 12px;
  color: #667085;
  line-height: 22px;
}

.placeholder-code {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f3f5f8;
  color: #3f4758;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
</style>
