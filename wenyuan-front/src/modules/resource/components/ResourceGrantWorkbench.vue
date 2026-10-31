<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="资源批量授权"
    :style="{ width: '820px', maxWidth: 'calc(100vw - 32px)' }"
    :bordered="false"
    :mask-closable="!loading"
  >
    <div class="grant-workbench-body">
      <n-card size="small" title="快捷复用" class="section-card">
        <n-grid :cols="12" :x-gap="12" :y-gap="8" responsive="screen">
          <n-grid-item :span="8">
            <n-select
              v-model:value="selectedTemplateId"
              :options="templateOptions"
              clearable
              placeholder="选择授权模板"
              @update:value="applySelectedTemplate"
            />
          </n-grid-item>
          <n-grid-item :span="4">
            <n-space justify="end">
              <n-button :disabled="!selectedTemplateId" @click="applySelectedTemplate">应用</n-button>
              <n-button :disabled="!selectedTemplateId" type="error" secondary @click="removeSelectedTemplate">
                删除
              </n-button>
            </n-space>
          </n-grid-item>
          <n-grid-item :span="8">
            <n-input v-model:value="templateName" placeholder="输入名称，将当前授权参数保存为模板" />
          </n-grid-item>
          <n-grid-item :span="2">
            <n-input-number
              v-model:value="templateValidDays"
              :min="1"
              :max="3650"
              clearable
              placeholder="有效天数"
              style="width: 100%"
            />
          </n-grid-item>
          <n-grid-item :span="2">
            <n-button block secondary :disabled="loading" @click="saveCurrentTemplate">保存模板</n-button>
          </n-grid-item>
        </n-grid>
      </n-card>

      <n-form :model="form" label-placement="left" label-width="96" :disabled="loading">
        <n-form-item label="授权范围" required>
          <n-radio-group v-model:value="form.scopeType" size="small">
            <n-radio-button value="SELECTED" :disabled="!normalizedSelectedResourceIds.length">已选资源</n-radio-button>
            <n-radio-button value="FILTER">当前筛选结果</n-radio-button>
            <n-radio-button value="CATEGORY">分类目录</n-radio-button>
            <n-radio-button value="IMPORT">资源清单</n-radio-button>
          </n-radio-group>
        </n-form-item>

        <n-alert v-if="form.scopeType === 'SELECTED'" type="info" :show-icon="false" class="scope-alert">
          固定授权当前已选择的 {{ normalizedSelectedResourceIds.length }} 条资源，后续新增资源不会自动继承。
        </n-alert>
        <template v-else-if="form.scopeType === 'FILTER'">
          <n-alert type="info" :show-icon="false" class="scope-alert">
            后端将按当前查询条件重新计算并固化资源快照，不受分页影响。
            <template v-if="filterSummary">当前条件：{{ filterSummary }}</template>
            <template v-else>当前没有筛选条件。</template>
          </n-alert>
          <n-form-item v-if="!filterHasCriteria" label="全部资源">
            <n-checkbox v-model:checked="form.confirmAll">确认授权当前可管理的全部资源</n-checkbox>
          </n-form-item>
        </template>
        <template v-else-if="form.scopeType === 'CATEGORY'">
          <n-form-item label="选择分类" required>
            <n-tree-select
              v-model:value="form.categoryIds"
              :options="categoryOptions"
              multiple
              cascade
              check-strategy="all"
              key-field="id"
              label-field="name"
              children-field="children"
              filterable
              clearable
              placeholder="请选择一个或多个分类"
            />
          </n-form-item>
          <n-form-item label="分类范围">
            <n-space vertical>
              <n-checkbox v-model:checked="form.categoryIncludeChildren">包含所选分类的全部子分类</n-checkbox>
              <n-checkbox
                v-model:checked="form.categoryIncludeFuture"
                :disabled="!canDynamicCategory"
              >
                自动包含以后新增的资源（仅资源管理员）
              </n-checkbox>
              <n-text v-if="!form.categoryIncludeFuture" depth="3">
                未勾选时，创建授权时会展开为固定资源快照。
              </n-text>
            </n-space>
          </n-form-item>
        </template>
        <n-form-item v-else label="资源清单" required>
          <n-space vertical style="width: 100%">
            <n-input
              v-model:value="form.resourceKeysText"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 8 }"
              placeholder="粘贴资源主键、资源编号或文物编号；每行一个，也可用逗号分隔"
            />
            <n-upload
              accept=".xlsx,.xls,.csv,.txt"
              :show-file-list="false"
              :default-upload="false"
              @before-upload="importResourceKeyFile"
            >
              <n-button secondary>导入 Excel / CSV / TXT 清单</n-button>
            </n-upload>
            <n-text depth="3">Excel 会读取所有非空单元格；长数字编号请在表格中设为文本格式。</n-text>
          </n-space>
        </n-form-item>

        <n-divider />
        <n-form-item label="授权对象">
          <n-radio-group v-model:value="subjectTypeModel" size="small">
            <n-radio-button value="GROUP">组织机构</n-radio-button>
            <n-radio-button value="USER">用户</n-radio-button>
            <n-radio-button value="ROLE">角色</n-radio-button>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="选择对象" required>
          <HGroupSelect
            v-if="form.subjectType === 'GROUP'"
            v-model:value="form.subjectIds"
            multiple
            cascade
            check-strategy="child"
            placeholder="请选择一个或多个机构"
          />
          <HUserSelect
            v-else-if="form.subjectType === 'USER'"
            v-model:value="form.subjectIds"
            multiple
            placeholder="输入姓名或账号搜索用户"
          />
          <HRoleSelect
            v-else
            v-model:value="form.subjectIds"
            multiple
            filterable
            placeholder="请选择一个或多个角色"
          />
        </n-form-item>
        <n-form-item v-if="form.subjectType === 'GROUP'" label="机构范围">
          <n-checkbox v-model:checked="form.subjectIncludeChildren">包含所选机构的下级机构</n-checkbox>
        </n-form-item>
        <n-form-item label="允许操作" required>
          <n-checkbox-group v-model:value="form.operationTypes">
            <n-space item-style="display: flex;" wrap>
              <n-checkbox
                v-for="option in RESOURCE_GRANT_OPERATION_OPTIONS"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </n-space>
          </n-checkbox-group>
        </n-form-item>
        <n-form-item label="授权等级" required>
          <n-select v-model:value="form.permissionLevel" :options="RESOURCE_PERMISSION_LEVEL_OPTIONS" />
        </n-form-item>
        <n-form-item label="有效期">
          <n-date-picker v-model:value="form.validRange" type="datetimerange" clearable style="width: 100%" />
        </n-form-item>
        <n-form-item label="授权说明">
          <n-input
            v-model:value="form.reason"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="填写授权用途或依据，便于后续审计"
          />
        </n-form-item>
      </n-form>

      <n-alert v-if="preview" :type="preview.unresolvedKeys?.length ? 'error' : 'success'" :show-icon="false">
        预检结果：当前匹配 {{ preview.resourceCount }} 条资源、{{ preview.subjectCount }} 个授权对象、
        {{ preview.operationCount }} 项操作；将保存 {{ preview.targetCount }} 个授权目标
        <template v-if="preview.dynamicTargetCount">，其中 {{ preview.dynamicTargetCount }} 个动态分类策略</template>。
        <template v-if="preview.duplicateResourceCount">
          已去除 {{ preview.duplicateResourceCount }} 条重复项。
        </template>
        <template v-if="preview.excludedByPermissionCount">
          有 {{ preview.excludedByPermissionCount }} 条资源高于所选授权等级，已排除。
        </template>
        <div v-if="preview.unresolvedKeys?.length" class="unresolved-keys">
          无法识别：{{ preview.unresolvedKeys.slice(0, 20).join('、') }}
        </div>
      </n-alert>

      <n-collapse class="history-collapse">
        <n-collapse-item title="最近授权记录（可复用或撤销）" name="history">
          <n-data-table
            size="small"
            :columns="historyColumns"
            :data="historyRows"
            :pagination="false"
            :max-height="260"
          />
        </n-collapse-item>
      </n-collapse>
    </div>

    <template #footer>
      <n-space justify="space-between">
        <n-button :disabled="loading" @click="loadReusableData">刷新模板和记录</n-button>
        <n-space>
          <n-button :disabled="loading" @click="visible = false">取消</n-button>
          <n-button :loading="loading" secondary @click="previewGrant">预检</n-button>
          <n-button type="primary" :loading="loading" @click="submitGrant">确认授权</n-button>
        </n-space>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref, watch } from 'vue';
import { NButton, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui';
import * as XLSX from 'xlsx';
import {
  createResourceGrantBatch,
  deleteResourceGrantTemplate,
  previewResourceGrantBatch,
  resourceGrantBatchList,
  resourceGrantTemplateList,
  revokeResourceGrantBatch,
  saveResourceGrantTemplate,
  RESOURCE_GRANT_OPERATION_OPTIONS,
  RESOURCE_PERMISSION_LEVEL_OPTIONS,
} from '../api/resource';
import {
  initialResourceGrantScope,
  normalizeResourceIds,
  resourceIdsForGrantScope,
} from '../utils/resourceGrantSelection';
import HGroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import HUserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import HRoleSelect from '@/modules/sys/components/form/field/RoleSelect.vue';

const props = defineProps<{
  show: boolean;
  selectedResourceIds: string[];
  currentFilter: Record<string, any>;
  categoryOptions: any[];
  canDynamicCategory?: boolean;
}>();
const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'success'): void;
}>();

const message = useMessage();
const loading = ref(false);
const preview = ref<any>(null);
const templates = ref<any[]>([]);
const historyRows = ref<any[]>([]);
const selectedTemplateId = ref<string | null>(null);
const templateName = ref('');
const templateValidDays = ref<number | null>(null);
const normalizedSelectedResourceIds = computed(() => normalizeResourceIds(props.selectedResourceIds));

const visible = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value),
});

const defaultForm = () => ({
  scopeType: initialResourceGrantScope(props.selectedResourceIds),
  confirmAll: false,
  categoryIds: [] as string[],
  categoryIncludeChildren: true,
  categoryIncludeFuture: false,
  resourceKeysText: '',
  subjectType: 'GROUP',
  subjectIds: [] as string[],
  subjectIncludeChildren: true,
  operationTypes: ['VIEW_METADATA', 'PREVIEW_LOW'] as string[],
  permissionLevel: 'LEVEL_1',
  validRange: null as [number, number] | null,
  reason: '',
});
const form = reactive(defaultForm());
const subjectTypeModel = computed<string>({
  get: () => form.subjectType,
  set: (value) => {
    if (value === form.subjectType) return;
    form.subjectType = value;
    form.subjectIds = [];
    preview.value = null;
  },
});

const filterKeys = [
  'keyword', 'code', 'culturalRelicNo', 'resourceType', 'fileExt', 'categoryId', 'status',
  'flowStatus', 'validationStatus', 'permissionLevel', 'tag', 'collectionTimeRange', 'dateTimeRange', 'createBy',
];
const currentGrantFilter = computed(() => Object.fromEntries(
  filterKeys
    .map((key) => [key, props.currentFilter?.[key]])
    .filter(([, value]) => value !== undefined && value !== null && value !== '' && (!Array.isArray(value) || value.length)),
));
const filterHasCriteria = computed(() => Object.keys(currentGrantFilter.value).length > 0);
const filterSummary = computed(() => Object.entries(currentGrantFilter.value)
  .slice(0, 6)
  .map(([key, value]) => `${key}=${Array.isArray(value) ? value.join('~') : value}`)
  .join('，'));
const templateOptions = computed(() => templates.value.map((item) => ({ label: item.name, value: item.id })));

watch(
  () => props.show,
  async (show) => {
    if (!show) return;
    Object.assign(form, defaultForm());
    preview.value = null;
    selectedTemplateId.value = null;
    templateName.value = '';
    templateValidDays.value = null;
    await loadReusableData();
  },
);
watch(
  () => [form.scopeType, form.permissionLevel],
  () => {
    preview.value = null;
  },
);

function parseResourceKeys() {
  return form.resourceKeysText.split(/[\n,，;；\t]+/).map((item) => item.trim()).filter(Boolean);
}

async function importResourceKeyFile(options: any) {
  const file = options?.file?.file as File | undefined;
  if (!file) return false;
  try {
    const extension = file.name.split('.').pop()?.toLowerCase();
    let values: string[] = [];
    if (extension === 'txt' || extension === 'csv') {
      values = (await file.text()).split(/[\n,，;；\t]+/);
    } else {
      const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' });
      values = workbook.SheetNames.flatMap((name) => {
        const rows = XLSX.utils.sheet_to_json<any[]>(workbook.Sheets[name], { header: 1, raw: false });
        return rows.flatMap((row) => row || []);
      });
    }
    const headers = new Set(['资源ID', '资源主键', '资源编号', '文物编号', 'ID', 'id']);
    const imported = values.map((value) => String(value ?? '').trim()).filter((value) => value && !headers.has(value));
    form.resourceKeysText = [...new Set([...parseResourceKeys(), ...imported])].join('\n');
    preview.value = null;
    message.success(`已导入 ${imported.length} 个资源标识`);
  } catch (error: any) {
    message.error(error?.message || '资源清单解析失败');
  }
  return false;
}

function buildRequest() {
  return {
    scopeType: form.scopeType,
    resourceIds: resourceIdsForGrantScope(form.scopeType, normalizedSelectedResourceIds.value),
    resourceKeys: form.scopeType === 'IMPORT' ? parseResourceKeys() : [],
    filter: form.scopeType === 'FILTER' ? currentGrantFilter.value : null,
    confirmAll: form.scopeType === 'FILTER' && form.confirmAll,
    categories: form.scopeType === 'CATEGORY'
      ? form.categoryIds.map((id) => ({
          id,
          includeChildren: form.categoryIncludeChildren,
          includeFuture: form.categoryIncludeFuture,
        }))
      : [],
    subjects: form.subjectIds.map((id) => ({
      type: form.subjectType,
      id,
      includeChildren: form.subjectType === 'GROUP' && form.subjectIncludeChildren,
    })),
    operationTypes: form.operationTypes,
    permissionLevel: form.permissionLevel,
    validFrom: form.validRange?.[0] || null,
    validUntil: form.validRange?.[1] || null,
    reason: form.reason || null,
  };
}

function validateForm() {
  if (form.scopeType === 'SELECTED' && !normalizedSelectedResourceIds.value.length) {
    message.warning('请先选择资源，或改用筛选结果、分类目录、资源清单');
    return false;
  }
  if (form.scopeType === 'FILTER' && !filterHasCriteria.value && !form.confirmAll) {
    message.warning('当前没有筛选条件，请勾选确认后再授权全部可管理资源');
    return false;
  }
  if (form.scopeType === 'CATEGORY' && !form.categoryIds.length) {
    message.warning('请选择分类目录');
    return false;
  }
  if (form.scopeType === 'IMPORT' && !parseResourceKeys().length) {
    message.warning('请输入资源清单');
    return false;
  }
  if (!form.subjectIds.length) {
    message.warning('请选择授权对象');
    return false;
  }
  if (!form.operationTypes.length) {
    message.warning('请至少选择一项允许操作');
    return false;
  }
  return true;
}

async function previewGrant() {
  if (!validateForm()) return;
  loading.value = true;
  try {
    const resp: any = await previewResourceGrantBatch(buildRequest());
    preview.value = resp?.data || null;
  } finally {
    loading.value = false;
  }
}

async function submitGrant() {
  if (!validateForm()) return;
  loading.value = true;
  try {
    const request = buildRequest();
    const previewResp: any = await previewResourceGrantBatch(request);
    preview.value = previewResp?.data || null;
    if (preview.value?.unresolvedKeys?.length) {
      message.error('资源清单中仍有无法识别的标识，请修正后再提交');
      return;
    }
    const resp: any = await createResourceGrantBatch(request);
    const result = resp?.data || {};
    message.success(`授权已生效：当前覆盖 ${result.resourceCount || 0} 条资源`);
    visible.value = false;
    emit('success');
  } finally {
    loading.value = false;
  }
}

async function loadReusableData() {
  const [templateResp, historyResp]: any[] = await Promise.all([
    resourceGrantTemplateList(),
    resourceGrantBatchList({ pageNum: 1, pageSize: 20 }),
  ]);
  templates.value = templateResp?.data || [];
  const page = historyResp?.data || {};
  historyRows.value = page.list || page.data || [];
}

function applyTemplate(template: any) {
  if (!template) return;
  form.subjectType = template.subjectType || 'GROUP';
  form.subjectIds = [...(template.subjectIds || [])];
  form.subjectIncludeChildren = !!template.includeChildren;
  form.operationTypes = [...(template.operationTypes || [])];
  form.permissionLevel = template.permissionLevel || 'LEVEL_1';
  form.reason = template.reason || '';
  if (template.validDays) {
    const start = Date.now();
    form.validRange = [start, start + Number(template.validDays) * 24 * 60 * 60 * 1000];
  } else {
    form.validRange = null;
  }
  preview.value = null;
}

function applySelectedTemplate() {
  applyTemplate(templates.value.find((item) => item.id === selectedTemplateId.value));
}

async function saveCurrentTemplate() {
  if (!templateName.value.trim()) {
    message.warning('请输入模板名称');
    return;
  }
  if (!form.subjectIds.length || !form.operationTypes.length) {
    message.warning('请先完整填写授权对象和允许操作');
    return;
  }
  loading.value = true;
  try {
    await saveResourceGrantTemplate({
      name: templateName.value.trim(),
      subjectType: form.subjectType,
      subjectIds: form.subjectIds,
      includeChildren: form.subjectType === 'GROUP' && form.subjectIncludeChildren,
      operationTypes: form.operationTypes,
      permissionLevel: form.permissionLevel,
      validDays: templateValidDays.value,
      reason: form.reason || null,
    });
    message.success('授权模板已保存');
    templateName.value = '';
    await loadReusableData();
  } finally {
    loading.value = false;
  }
}

async function removeSelectedTemplate() {
  if (!selectedTemplateId.value) return;
  await deleteResourceGrantTemplate(selectedTemplateId.value);
  selectedTemplateId.value = null;
  message.success('授权模板已删除');
  await loadReusableData();
}

function reuseHistory(row: any) {
  const subjects = row.subjects || [];
  const subjectType = subjects[0]?.type || 'GROUP';
  form.subjectType = subjectType;
  form.subjectIds = subjects.filter((item: any) => item.type === subjectType).map((item: any) => item.id);
  form.subjectIncludeChildren = subjects.some((item: any) => item.includeChildren);
  form.operationTypes = [...(row.operationTypes || [])];
  form.permissionLevel = row.permissionLevel || 'LEVEL_1';
  form.reason = row.reason || '';
  const now = Date.now();
  form.validRange = row.validUntil && row.validUntil > now
    ? [Math.max(now, row.validFrom || now), row.validUntil]
    : null;
  preview.value = null;
  message.success('已复用最近授权参数，请重新确认授权范围');
}

async function revokeHistory(row: any) {
  await revokeResourceGrantBatch(row.batchId);
  message.success('授权批次已撤销');
  await loadReusableData();
  emit('success');
}

const historyColumns: DataTableColumns<any> = [
  {
    title: '创建时间',
    key: 'createTime',
    width: 155,
    render: (row) => row.createTime ? new Date(row.createTime).toLocaleString() : '-',
  },
  {
    title: '范围',
    key: 'scope',
    width: 90,
    render: (row) => Number(row.dynamicTargetCount || 0) > 0 ? '动态分类' : '资源快照',
  },
  { title: '资源数', key: 'resourceCount', width: 75 },
  {
    title: '状态',
    key: 'status',
    width: 85,
    render: (row) => h(NTag, { size: 'small', type: row.status === 'SUCCESS' ? 'success' : 'default' }, {
      default: () => row.status === 'SUCCESS' ? '已生效' : '已撤销',
    }),
  },
  { title: '说明', key: 'reason', ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 135,
    render: (row) => h(NSpace, { size: 6 }, {
      default: () => [
        h(NButton, { size: 'tiny', secondary: true, onClick: () => reuseHistory(row) }, { default: () => '复用' }),
        row.status === 'SUCCESS'
          ? h(NButton, { size: 'tiny', type: 'error', secondary: true, onClick: () => revokeHistory(row) }, { default: () => '撤销' })
          : null,
      ],
    }),
  },
];
</script>

<style scoped>
.grant-workbench-body {
  max-height: min(68vh, 720px);
  overflow-y: auto;
  padding-right: 4px;
}

.section-card,
.scope-alert {
  margin-bottom: 16px;
}

.history-collapse {
  margin-top: 16px;
}

.unresolved-keys {
  margin-top: 6px;
  word-break: break-all;
}
</style>
