<template>
  <ResourcePage fill title="资源台账">
  <div class="resource-management-page">
    <n-card class="resource-category-sidebar" size="small" :bordered="false">
      <template #header>
        <n-space align="center">
          <n-icon size="18"><IconMdiFolderMultiple /></n-icon>
          <span>资源分类</span>
        </n-space>
      </template>
      <template #header-extra>
        <n-button size="tiny" secondary :loading="categoryTreeLoading" @click="loadCategoryTree">
          <template #icon
            ><n-icon><IconMdiRefresh /></n-icon
          ></template>
          刷新
        </n-button>
      </template>
      <n-input
        v-model:value="categoryTreeFilter"
        class="category-tree-filter"
        placeholder="过滤分类"
        clearable
        size="small"
      >
        <template #prefix
          ><n-icon><IconMdiMagnify /></n-icon
        ></template>
      </n-input>
      <n-button class="category-all-button" size="small" quaternary block @click="clearCategoryTreeFilter">
        全部资源
      </n-button>
      <n-scrollbar class="resource-category-scroll">
        <n-tree
          block-line
          :data="categoryTreeData"
          :pattern="categoryTreeFilter"
          :selected-keys="selectedCategoryKeys"
          key-field="id"
          label-field="name"
          children-field="children"
          :loading="categoryTreeLoading"
          :expand-on-click="true"
          :default-expand-all="true"
          @update:selected-keys="onCategoryTreeSelect"
        />
      </n-scrollbar>
    </n-card>

    <div class="resource-table-panel">
      <h-page-data-table
        class="resource-data-table"
        title="资源台账"
        ref="refPageTable"
        :fetch-data="fetchResourceRows"
        export-label="导出资源列表 Excel"
        scroll-x="1900"
        :params="paramsForm"
        :fun-api-delete="del"
        :is-edit="false"
        :is-delete="false"
        permission="RESOURCE"
        :selection-options="batchSelectionOptions"
        clear-selection-on-fetch
        :columns="columns"
        @add="handleAdd"
        @rowEdit="handleEdit"
        @rowView="handleView"
      >
        <template #activeBtn="{ checkedTableData }">
          <n-button
            v-if="canGrantPermission"
            type="primary"
            secondary
            @click="openGrantWorkbench(checkedTableData)"
          >
            <template #icon><n-icon><IconMdiKeyChain /></n-icon></template>
            资源授权{{ checkedTableData?.length ? `（${checkedTableData.length}）` : '' }}
          </n-button>
        </template>
        <template #toolbar-extra>
          <n-space>
            <n-button v-if="canApplyPermission" secondary @click="accessApplicationShow = true">
              <template #icon><n-icon><IconMdiFileDocumentEditOutline /></n-icon></template>
              权限申请
            </n-button>
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button v-if="canCreatePermission" type="primary" secondary @click="openBatchUpload">
                  <template #icon>
                    <n-icon><IconMdiCloudUpload /></n-icon>
                  </template>
                  批量上传
                </n-button>
              </template>
              批量上传资源文件
            </n-tooltip>
          </n-space>
        </template>

        <template #queryForm>
          <n-form-item label="关键词" path="keyword">
            <n-input v-model:value="paramsForm.keyword" placeholder="名称/描述/标签/编码" clearable />
          </n-form-item>
          <n-form-item label="编号" path="code">
            <n-input v-model:value="paramsForm.code" placeholder="资源编号" clearable />
          </n-form-item>
          <n-form-item label="文物编号" path="culturalRelicNo">
            <n-input v-model:value="paramsForm.culturalRelicNo" placeholder="文物编号" clearable />
          </n-form-item>
          <n-form-item label="资源类型" path="resourceType">
            <h-dict-select type="resource_type" placeholder="全部" v-model:value="paramsForm.resourceType" clearable>
            </h-dict-select>
          </n-form-item>
          <n-form-item label="文件格式" path="fileExt">
            <n-input v-model:value="paramsForm.fileExt" placeholder="如 osgb/pdf/tif" clearable />
          </n-form-item>
          <n-form-item label="分类" path="categoryId">
            <CategoryTreeSelect v-model:value="paramsForm.categoryId" style="width: 200px" />
          </n-form-item>
          <n-form-item label="状态" path="status">
            <h-dict-select type="resource_status" placeholder="全部" v-model:value="paramsForm.status" clearable>
            </h-dict-select>
          </n-form-item>
          <n-form-item label="审核状态" path="flowStatus">
            <n-select
              v-model:value="paramsForm.flowStatus"
              :options="RESOURCE_FLOW_STATUS_OPTIONS"
              placeholder="全部"
              clearable
              style="width: 140px"
            />
          </n-form-item>
          <n-form-item label="校验状态" path="validationStatus">
            <h-dict-select
              type="resource_validation_status"
              placeholder="全部"
              v-model:value="paramsForm.validationStatus"
              clearable
            >
            </h-dict-select>
          </n-form-item>
          <n-form-item label="权限等级" path="permissionLevel">
            <n-select
              v-model:value="paramsForm.permissionLevel"
              :options="RESOURCE_PERMISSION_LEVEL_OPTIONS"
              placeholder="全部"
              clearable
            />
          </n-form-item>
          <n-form-item label="标签" path="tag">
            <n-input v-model:value="paramsForm.tag" placeholder="按标签精确匹配" clearable />
          </n-form-item>
          <n-form-item label="文物年代"><n-input v-model:value="paramsForm.era" placeholder="如清代" clearable /></n-form-item>
          <n-form-item label="资源用途"><n-select v-model:value="paramsForm.purpose" :options="PURPOSES" clearable placeholder="全部用途" style="width:160px" /></n-form-item>
          <n-form-item label="采集时间" path="collectionTimeRange">
            <n-date-picker v-model:value="paramsForm.collectionTimeRange" type="daterange" clearable />
          </n-form-item>
          <n-form-item label="创建时间" path="dateTimeRange">
            <n-date-picker v-model:value="paramsForm.dateTimeRange" type="daterange" clearable />
          </n-form-item>
        </template>

        <!-- 创建 / 编辑 表单 -->
        <ResourceForm
          v-model:show="formModel"
          :mode="formMode"
          :title="formTitle"
          :initial="form"
          :ref-table="refPageTable"
          @success="onFormSuccess"
        >
          <template v-if="$slots.extendForm" #extendForm="slotProps">
            <slot name="extendForm" v-bind="slotProps" />
          </template>
        </ResourceForm>

        <!-- 版本管理 -->
        <VersionDrawer
          v-model:show="versionModel"
          :resource-id="activeRow?.id"
          :resource-name="activeRow?.name"
          :current-version="activeRow?.currentVersion || 1"
          :editable="isVersionManageable(activeRow)"
          @changed="reloadTable"
        />

        <!-- 关联管理 -->
        <RelationDrawer
          v-model:show="relationModel"
          :resource-id="activeRow?.id"
          :resource-name="activeRow?.name"
          :editable="isRelationManageable(activeRow)"
        />

        <!-- 资源预览（点击名称列触发）-->
        <ResourcePreview v-model:show="previewShow" :resource-id="previewId" :show-flow-log="true" />
        <n-modal v-model:show="progressShow" preset="card" title="资源审批进度" style="width:min(900px,calc(100vw - 32px))"><ResourceApprovalProgress v-if="progressShow && progressRow" :business-id="progressRow.id" business-type="RESOURCE_IMPORT" :fetch-flow="flowInfo" /></n-modal>
        <n-modal v-model:show="publicationShow" preset="card" title="资源渠道发布" style="width:min(1000px,calc(100vw - 32px))"><ResourcePublicationPanel v-if="publicationShow && publicationRow" :subject-id="publicationRow.id" subject-type="RESOURCE" /></n-modal>

        <ResourceGrantWorkbench
          v-model:show="batchGrantShow"
          :selected-resource-ids="batchGrantIds"
          :current-filter="paramsForm"
          :category-options="categoryTreeData"
          :can-dynamic-category="canDynamicGrantPermission"
          @success="refreshAfterResourceOperation"
        />
        <ResourceAccessApplicationModal v-model:show="accessApplicationShow" />

        <n-modal
          v-model:show="batchTagShow"
          preset="dialog"
          :title="batchTagReplace ? '批量替换标签' : '批量追加标签'"
          positive-text="确认"
          negative-text="取消"
          @positive-click="confirmBatchTags"
        >
          <n-input
            v-model:value="batchTagsInput"
            type="textarea"
            placeholder="多个标签用逗号或换行分隔"
            :autosize="{ minRows: 3, maxRows: 6 }"
          />
        </n-modal>

        <n-modal
          v-model:show="batchUploadShow"
          preset="card"
          title="批量上传资源"
          class="batch-upload-modal"
          :style="batchUploadModalStyle"
          :content-style="batchUploadModalContentStyle"
          :bordered="false"
          :mask-closable="!batchUploadLoading"
        >
          <n-form :model="batchUploadForm" label-placement="left" label-width="90" :disabled="batchUploadLoading">
            <n-grid :cols="24" :x-gap="12" :y-gap="4">
              <n-grid-item :span="24">
                <n-form-item label="上传方式">
                  <n-radio-group v-model:value="batchUploadMode" size="small">
                    <n-radio-button v-for="item in batchUploadModeOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </n-radio-button>
                  </n-radio-group>
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="24">
                <n-form-item label="资源文件">
                  <n-upload
                    v-model:file-list="batchUploadFileList"
                    :multiple="batchUploadMode === 'files'"
                    :max="batchUploadMode === 'package' ? 1 : undefined"
                    :accept="batchUploadMode === 'package' ? '.zip,application/zip,application/x-zip-compressed' : undefined"
                    :default-upload="false"
                    :show-retry-button="false"
                    @change="onBatchUploadFileChange"
                  >
                    <n-upload-dragger>
                      <div class="batch-upload-drop">
                        <n-icon size="38" depth="3"><IconMdiCloudUpload /></n-icon>
                        <div>{{ batchUploadMode === 'package' ? '点击或拖拽一个ZIP资源包到此处' : '点击或拖拽多个资源文件到此处' }}</div>
                        <div class="batch-upload-hint">
                          {{
                            batchUploadMode === 'package'
                              ? '适用于 3D Tiles、glTF、OBJ、OSGB 等复合资源；整个ZIP创建一条资源和一张审核单'
                              : '每个文件会创建一条资源记录，支持 MVIEW 等单文件三维模型，名称默认使用文件名'
                          }}
                        </div>
                      </div>
                    </n-upload-dragger>
                  </n-upload>
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="12">
                <n-form-item label="分类" path="categoryId">
                  <CategoryTreeSelect v-model:value="batchUploadForm.categoryId" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="12">
                <n-form-item label="资源类型" path="resourceType">
                  <n-select
                    v-model:value="batchUploadForm.resourceType"
                    :options="RESOURCE_TYPE_OPTIONS"
                    placeholder="留空按扩展名推断"
                    clearable
                  />
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="12">
                <n-form-item label="采集时间" path="collectionTime">
                  <n-date-picker v-model:value="batchUploadForm.collectionTime" type="datetime" clearable style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="12">
                <n-form-item label="权限等级" path="permissionLevel">
                  <n-select
                    v-model:value="batchUploadForm.permissionLevel"
                    :options="RESOURCE_PERMISSION_LEVEL_OPTIONS"
                    placeholder="请选择权限等级"
                  />
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="12">
                <n-form-item label="文物编号" path="culturalRelicNo">
                  <n-input v-model:value="batchUploadForm.culturalRelicNo" placeholder="可选，批量共用" clearable />
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="12">
                <n-form-item label="排序" path="sort">
                  <n-input-number v-model:value="batchUploadForm.sort" :min="0" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="24">
                <n-form-item label="标签" path="tags">
                  <n-dynamic-tags v-model:value="batchUploadTagsArr" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item :span="24">
                <n-form-item label="描述" path="description">
                  <n-input
                    v-model:value="batchUploadForm.description"
                    type="textarea"
                    :autosize="{ minRows: 2, maxRows: 4 }"
                    placeholder="可选，批量共用"
                  />
                </n-form-item>
              </n-grid-item>
            </n-grid>
          </n-form>
          <div v-if="batchUploadFiles.length" class="batch-upload-count">已选择 {{ batchUploadFiles.length }} 个文件</div>
          <n-data-table
            v-if="batchUploadResults.length"
            class="batch-upload-result"
            size="small"
            :columns="batchUploadResultColumns"
            :data="batchUploadResults"
            :pagination="false"
          />
          <template #footer>
            <n-space justify="end">
              <n-button :disabled="batchUploadLoading" @click="batchUploadShow = false">关闭</n-button>
              <n-button type="primary" :loading="batchUploadLoading" @click="submitBatchUpload">开始上传</n-button>
            </n-space>
          </template>
        </n-modal>
      </h-page-data-table>
    </div>
  </div>
  </ResourcePage>
</template>

<script lang="ts" setup>
import ResourcePage from '../components/ResourcePage.vue';
import { computed, h, onMounted, ref, watch, type CSSProperties } from 'vue';
import { useRoute } from 'vue-router';
import { PURPOSES } from '../api/business';
import { exportList, flowInfo, RESOURCE_VISIBILITY_OPTIONS } from '../api/resource';
import ResourceApprovalProgress from '../components/ResourceApprovalProgress.vue';
import ResourcePublicationPanel from '../components/ResourcePublicationPanel.vue';
import { NButton, NSpace, NTag, NTooltip, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import {
  archive,
  batchAdd,
  batchArchive,
  batchDel,
  batchPublish,
  batchUpdateTags,
  del,
  get,
  list,
  packageAdd,
  publish,
  submitImport,
  RESOURCE_FLOW_STATUS_OPTIONS,
  RESOURCE_FLOW_STATUS_TYPE_MAP,
  RESOURCE_PERMISSION_LEVEL_OPTIONS,
  RESOURCE_TYPE_OPTIONS,
  RESOURCE_VALIDATION_STATUS_OPTIONS,
  RESOURCE_VALIDATION_STATUS_TYPE_MAP,
} from '../api/resource';
import { tree as fetchCategoryTree } from '../api/category';
import CategoryTreeSelect from '../components/CategoryTreeSelect.vue';
import ResourceForm from '../components/ResourceForm.vue';
import ResourcePreview from '../components/ResourcePreview.vue';
import VersionDrawer from '../components/VersionDrawer.vue';
import RelationDrawer from '../components/RelationDrawer.vue';
import ResourceGrantWorkbench from '../components/ResourceGrantWorkbench.vue';
import ResourceAccessApplicationModal from '../components/ResourceAccessApplicationModal.vue';
import { normalizeResourceIds } from '../utils/resourceGrantSelection';
import { confirm } from '@/utils';
import { useAuthStore } from '@/store';

const fetchResourceRows = (params: any) => Number(params.pageSize) < 0 ? exportList(params) : list(params);
const refPageTable = ref();
const route = useRoute();
const progressRow = ref<any>(); const progressShow = ref(false);
const publicationRow = ref<any>(); const publicationShow = ref(false);
const message = useMessage();
const authStore = useAuthStore();
const categoryTreeData = ref<any[]>([]);
const categoryTreeFilter = ref('');
const categoryTreeLoading = ref(false);
const selectedCategoryKeys = ref<string[]>([]);
const RESOURCE_CONTENT_EDITABLE_FLOW_STATUSES = ['DRAFT', 'REJECT', 'CANCEL'];
const canCreatePermission = computed(() => authStore.hasPermission('RESOURCE_ALL,RESOURCE_CREATE'));
const canEditPermission = computed(() => authStore.hasPermission('RESOURCE_ALL,RESOURCE_EDIT'));
const canDeletePermission = computed(() => authStore.hasPermission('RESOURCE_ALL,RESOURCE_DELETE'));
const canAuditPermission = computed(() => authStore.hasPermission('RESOURCE_ALL,RESOURCE_AUDIT'));
const canGrantPermission = computed(() => authStore.hasPermission('RESOURCE_ALL,RESOURCE_GRANT'));
const canDynamicGrantPermission = computed(() => authStore.hasPermission('ADMIN,RESOURCE_ALL'));
const canApplyPermission = computed(() => authStore.hasPermission('RESOURCE_ALL,RESOURCE_APPLY'));
const canSubmitPermission = computed(() =>
  authStore.hasPermission('RESOURCE_ALL,RESOURCE_AUDIT,RESOURCE_CREATE'),
);
const batchSelectionOptions = computed(() => [
  ...(canSubmitPermission.value
    ? [{ label: '提交入库', key: 'submitImport', onSelect: handleBatchSubmit }]
    : []),
  ...(canAuditPermission.value
    ? [{ label: '批量发布', key: 'publish', onSelect: handleBatchPublish }]
    : []),
  ...(canEditPermission.value
    ? [
        { label: '批量归档', key: 'archive', onSelect: handleBatchArchive },
        { label: '追加标签', key: 'appendTags', onSelect: (ids: string[], rows: any[]) => openBatchTags(ids, false, rows) },
        { label: '替换标签', key: 'replaceTags', onSelect: (ids: string[], rows: any[]) => openBatchTags(ids, true, rows) },
      ]
    : []),
  ...(canGrantPermission.value
    ? [{ label: '批量授权', key: 'grant', onSelect: openBatchGrant }]
    : []),
  ...(canDeletePermission.value
    ? [{ label: '批量删除', key: 'delete', onSelect: handleBatchDelete }]
    : []),
]);

// ===== props =====
const props = defineProps({
  stationId: { type: String, default: '' },
});

// ===== 查询参数 =====
const paramsForm = ref<Record<string, any>>({
  keyword: '', era: '', purpose: null,
  code: '',
  culturalRelicNo: '',
  resourceType: null,
  fileExt: '',
  categoryId: null,
  status: '',
  flowStatus: '',
  validationStatus: '',
  permissionLevel: '',
  tag: '',
  collectionTimeRange: null,
  dateTimeRange: null,
  stationId: props.stationId || '',
});

watch(() => [route.query.resourceType, route.query.purpose, route.query.flowStatus, route.query.status], () => {
  for (const key of ['resourceType', 'purpose', 'flowStatus', 'status']) {
    const value = route.query[key]; paramsForm.value[key] = typeof value === 'string' ? value : null;
  }
  refPageTable.value?.handleQuery?.();
}, { immediate: true });

// ===== 表单 =====
const defaultForm = {
  id: '',
  name: '',
  code: '',
  culturalRelicNo: '',
  categoryId: '',
  resourceType: '',
  collectionTime: null,
  thumbnailPath: '',
  description: '',
  tags: '',
  metadata: '',
  permissionLevel: 'LEVEL_1',
  visibility: 'GROUP',
  validationStatus: '',
  sort: 0,
};
const form = ref<Record<string, any>>(cloneDeep(defaultForm));
const formModel = ref(false);
const formMode = ref<'add' | 'edit' | 'view'>('add');
const formTitle = ref('');

// ===== 抽屉状态 =====
const activeRow = ref<any>(null);
const versionModel = ref(false);
const relationModel = ref(false);
const batchTagShow = ref(false);
const batchTagsInput = ref('');
const batchTagReplace = ref(false);
const batchTagIds = ref<string[]>([]);
const batchGrantShow = ref(false);
const batchGrantIds = ref<string[]>([]);
const accessApplicationShow = ref(false);
const batchUploadShow = ref(false);
const batchUploadLoading = ref(false);
const batchUploadMode = ref<'files' | 'package'>('files');
const batchUploadModeOptions = [
  { label: '普通批量', value: 'files' },
  { label: '资源包 ZIP', value: 'package' },
];
const batchUploadFileList = ref<any[]>([]);
const batchUploadFiles = ref<File[]>([]);
const batchUploadResults = ref<any[]>([]);
const batchUploadTagsArr = ref<string[]>([]);
const batchUploadForm = ref<Record<string, any>>({
  categoryId: '',
  resourceType: '',
  collectionTime: null,
  permissionLevel: 'LEVEL_1',
  visibility: 'GROUP',
  culturalRelicNo: '',
  sort: 0,
  tags: '',
  description: '',
});
const batchUploadModalStyle: CSSProperties = {
  width: '640px',
  maxWidth: 'calc(100vw - 32px)',
};
const batchUploadModalContentStyle: CSSProperties = {
  maxHeight: 'min(62vh, 520px)',
  overflowY: 'auto',
  paddingRight: '4px',
};

// ===== 预览 =====
const previewShow = ref(false);
const previewId = ref('');

async function loadCategoryTree() {
  categoryTreeLoading.value = true;
  try {
    const resp: any = await fetchCategoryTree({});
    categoryTreeData.value = resp.data || [];
  } finally {
    categoryTreeLoading.value = false;
  }
}

function onCategoryTreeSelect(keys: string[]) {
  selectedCategoryKeys.value = keys;
  const categoryId = keys[0] || '';
  paramsForm.value.categoryId = categoryId || null;
  reloadTable();
}

function clearCategoryTreeFilter() {
  selectedCategoryKeys.value = [];
  paramsForm.value.categoryId = null;
  reloadTable();
}

function openPreview(row: any) {
  previewId.value = row.id;
  previewShow.value = true;
}

function reloadTable() {
  refPageTable.value?.handleRefresh?.() || refPageTable.value?.handleQuery?.();
}

function clearResourceSelection() {
  refPageTable.value?.clearCheckedRows?.();
  batchGrantIds.value = [];
}

function refreshAfterResourceOperation() {
  clearResourceSelection();
  reloadTable();
}

watch(
  () => paramsForm.value.categoryId,
  (categoryId) => {
    selectedCategoryKeys.value = categoryId ? [categoryId] : [];
  },
);

watch(
  batchUploadTagsArr,
  (arr) => {
    batchUploadForm.value.tags = (arr || []).join(',');
  },
  { deep: true },
);

watch(batchUploadMode, () => {
  batchUploadFileList.value = [];
  batchUploadFiles.value = [];
  batchUploadResults.value = [];
});

onMounted(() => {
  loadCategoryTree();
});

function handleAdd() {
  if (!canCreatePermission.value) return;
  formMode.value = 'add';
  form.value = { ...cloneDeep(defaultForm), stationId: props.stationId || '' };
  formTitle.value = '创建资源';
  formModel.value = true;
}

async function handleEdit(row: any) {
  if (!canEditRow(row)) {
    return;
  }
  formMode.value = 'edit';
  formTitle.value = `编辑「${row.name}」`;
  try {
    const resp: any = await get(row.id);
    form.value = { ...cloneDeep(defaultForm), ...(resp.data || row) };
  } catch {
    form.value = { ...cloneDeep(defaultForm), ...row };
  }
  formModel.value = true;
}

async function handleView(row: any) {
  formMode.value = 'view';
  formTitle.value = `查看「${row.name}」`;
  try {
    const resp: any = await get(row.id);
    form.value = { ...cloneDeep(defaultForm), ...(resp.data || row) };
  } catch {
    form.value = { ...cloneDeep(defaultForm), ...row };
  }
  formModel.value = true;
}

function onFormSuccess() {
  reloadTable();
}

function openVersion(row: any) {
  activeRow.value = row;
  versionModel.value = true;
}

function openRelation(row: any) {
  activeRow.value = row;
  relationModel.value = true;
}

function isContentEditable(row: any) {
  return RESOURCE_CONTENT_EDITABLE_FLOW_STATUSES.includes(row?.flowStatus);
}

function hasEditDataPermission(row: any) {
  return row?.canEditData === true;
}

function hasGrantDataPermission(row: any) {
  return row?.canGrantData === true;
}

function canEditRow(row: any) {
  return canEditPermission.value && hasEditDataPermission(row) && isContentEditable(row) && row?.status !== 'PUBLISHED';
}

function canDeleteRow(row: any) {
  return canDeletePermission.value && hasEditDataPermission(row) && isContentEditable(row) && row?.status !== 'PUBLISHED';
}

function canSubmitRow(row: any) {
  return (
    canSubmitPermission.value &&
    hasEditDataPermission(row) &&
    isContentEditable(row) &&
    row?.validationStatus !== 'FAILED' &&
    row?.status !== 'PUBLISHED'
  );
}

function canPublishRow(row: any) {
  return (
    canAuditPermission.value &&
    hasEditDataPermission(row) &&
    row?.flowStatus === 'PASS' &&
    row?.validationStatus !== 'FAILED' &&
    row?.status !== 'PUBLISHED'
  );
}

function canArchiveRow(row: any) {
  return canEditPermission.value && hasEditDataPermission(row) && row?.status === 'PUBLISHED';
}

function isVersionManageable(row: any) {
  return canEditPermission.value && hasEditDataPermission(row) && Boolean(row?.id) && row?.flowStatus !== 'VOID';
}

function isRelationManageable(row: any) {
  return canEditPermission.value && hasEditDataPermission(row) && Boolean(row?.id) && row?.flowStatus !== 'VOID';
}

function handleConfirmArchive(row: any) {
  if (!canArchiveRow(row)) return;
  confirm({
    type: 'warning',
    title: '请注意',
    content: `确定将「${row.name}」归档吗？归档后将不再对外显示。`,
    onPositiveClick: async () => {
      await archive(row.id);
      message.success('归档成功');
      refreshAfterResourceOperation();
    },
  });
}

function handleConfirmPublish(row: any) {
  if (!canPublishRow(row)) return;
  confirm({
    type: 'info',
    title: '请注意',
    content: `确定发布「${row.name}」？可见范围：${RESOURCE_VISIBILITY_OPTIONS.find(option => option.value === row.visibility)?.label || row.visibility || "所属机构"}。发布保留当前访问权限；门户或小程序请在“渠道发布”中分别设置。`,
    onPositiveClick: async () => {
      await publish(row.id);
      message.success('发布成功');
      refreshAfterResourceOperation();
    },
  });
}

function handleConfirmSubmit(row: any) {
  if (!canSubmitRow(row)) return;
  confirm({
    type: 'info',
    title: '提交入库审核',
    content: `确定提交「${row.name}」进入入库审核流程吗？`,
    onPositiveClick: async () => {
      await submitImport(row.id);
      message.success('已提交入库审核');
      refreshAfterResourceOperation();
    },
  });
}

function handleConfirmDelete(row: any) {
  if (!canDeleteRow(row)) return;
  confirm({
    type: 'warning',
    title: '删除资源',
    content: `确定删除「${row.name}」吗？删除后将同步移除版本记录和关联关系。`,
    onPositiveClick: async () => {
      await del(row.id);
      message.success('删除成功');
      refreshAfterResourceOperation();
    },
  });
}

function handleBatchSubmit(ids: string[], rows: any[] = []) {
  if (!validateBatchRows(ids, rows, canSubmitRow, '提交入库')) return;
  confirm({
    type: 'info',
    title: '批量提交入库',
    content: `确定提交选中的 ${ids.length} 条资源进入入库审核吗？`,
    onPositiveClick: async () => {
      await Promise.all(ids.map((id) => submitImport(id)));
      message.success('提交成功');
      refreshAfterResourceOperation();
    },
  });
}

function handleBatchPublish(ids: string[], rows: any[] = []) {
  if (!validateBatchRows(ids, rows, canPublishRow, '发布')) return;
  confirm({
    type: 'info',
    title: '批量发布',
    content: `确定发布选中的 ${ids.length} 条资源吗？每条资源保留原有可见范围和授权；门户、小程序渠道需另外设置。`,
    onPositiveClick: async () => {
      await batchPublish(ids);
      message.success('发布成功');
      refreshAfterResourceOperation();
    },
  });
}

function handleBatchArchive(ids: string[], rows: any[] = []) {
  if (!validateBatchRows(ids, rows, canArchiveRow, '归档')) return;
  confirm({
    type: 'warning',
    title: '批量归档',
    content: `确定归档选中的 ${ids.length} 条资源吗？`,
    onPositiveClick: async () => {
      await batchArchive(ids);
      message.success('归档成功');
      refreshAfterResourceOperation();
    },
  });
}

function handleBatchDelete(ids: string[], rows: any[] = []) {
  if (!validateBatchRows(ids, rows, canDeleteRow, '删除')) return;
  confirm({
    type: 'warning',
    title: '批量删除资源',
    content: `确定删除选中的 ${ids.length} 条资源吗？删除后将同步移除版本记录和关联关系。`,
    onPositiveClick: async () => {
      await batchDel(ids);
      message.success('删除成功');
      refreshAfterResourceOperation();
    },
  });
}

function validateBatchRows(
  ids: string[],
  rows: any[],
  predicate: (row: any) => boolean,
  actionLabel: string,
) {
  if (!ids?.length) return false;
  const invalidCount = (rows || []).filter((row) => !predicate(row)).length;
  if (rows.length !== ids.length || invalidCount > 0) {
    message.warning(`选中项中存在不能${actionLabel}的资源，请按状态和权限重新选择`);
    return false;
  }
  return true;
}

function openBatchGrant(ids: Array<string | number>, rows: any[] = []) {
  const resourceIds = normalizeResourceIds(ids);
  if (!canGrantPermission.value || !validateBatchRows(resourceIds, rows, hasGrantDataPermission, '授权')) return;
  batchGrantIds.value = resourceIds;
  batchGrantShow.value = true;
}

function openGrantWorkbench(rows: any[] = []) {
  if (rows.length) {
    openBatchGrant(rows.map((row) => row.id), rows);
    return;
  }
  batchGrantIds.value = [];
  batchGrantShow.value = true;
}

function openBatchTags(ids: string[], replace: boolean, rows: any[] = []) {
  if (!validateBatchRows(ids, rows, canEditRow, replace ? '替换标签' : '追加标签')) return;
  batchTagIds.value = ids || [];
  batchTagReplace.value = replace;
  batchTagsInput.value = '';
  batchTagShow.value = true;
}

async function confirmBatchTags() {
  const tags = batchTagsInput.value
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean);
  if (!tags.length) {
    message.warning('请输入标签');
    return false;
  }
  await batchUpdateTags({ ids: batchTagIds.value, tags, replace: batchTagReplace.value });
  message.success('标签已更新');
  batchTagIds.value = [];
  refreshAfterResourceOperation();
  return true;
}

function openBatchUpload() {
  if (!canCreatePermission.value) return;
  batchUploadMode.value = 'files';
  batchUploadForm.value = {
    categoryId: paramsForm.value.categoryId || '',
    resourceType: '',
    collectionTime: null,
    permissionLevel: 'LEVEL_1',
    culturalRelicNo: '',
    sort: 0,
    tags: '',
    description: '',
  };
  batchUploadTagsArr.value = [];
  batchUploadFileList.value = [];
  batchUploadFiles.value = [];
  batchUploadResults.value = [];
  batchUploadShow.value = true;
}

function onBatchUploadFileChange({ fileList }: any) {
  const nextFileList = batchUploadMode.value === 'package' ? (fileList || []).slice(-1) : fileList || [];
  batchUploadFileList.value = nextFileList;
  batchUploadFiles.value = nextFileList.map((item: any) => item.file).filter(Boolean);
}

function buildBatchUploadVo() {
  const vo: Record<string, any> = { ...batchUploadForm.value };
  Object.keys(vo).forEach((key) => {
    if (vo[key] === '' || vo[key] === null || vo[key] === undefined) {
      delete vo[key];
    }
  });
  return vo;
}

async function submitBatchUpload() {
  if (!canCreatePermission.value) return;
  if (!batchUploadFiles.value.length) {
    message.warning('请选择要上传的资源文件');
    return;
  }
  if (batchUploadMode.value === 'package') {
    const file = batchUploadFiles.value[0];
    if (!file || !file.name?.toLowerCase().endsWith('.zip')) {
      message.warning('资源包模式请选择一个ZIP文件');
      return;
    }
    batchUploadLoading.value = true;
    try {
      const resp: any = await packageAdd(file, buildBatchUploadVo());
      batchUploadResults.value = [
        {
          fileName: file.name,
          resourceId: resp?.data?.id,
          success: true,
          message: '资源包上传成功',
        },
      ];
      message.success('资源包上传成功');
      reloadTable();
    } finally {
      batchUploadLoading.value = false;
    }
    return;
  }
  batchUploadLoading.value = true;
  try {
    const resp: any = await batchAdd(batchUploadFiles.value, buildBatchUploadVo());
    const result = resp?.data || {};
    batchUploadResults.value = result.items || [];
    if (result.failedCount > 0) {
      message.warning(`上传完成，成功 ${result.successCount || 0} 个，失败 ${result.failedCount} 个`);
    } else {
      message.success(`上传成功 ${result.successCount || batchUploadFiles.value.length} 个资源`);
    }
    if ((result.successCount || 0) > 0) {
      reloadTable();
    }
  } finally {
    batchUploadLoading.value = false;
  }
}

const batchUploadResultColumns = [
  { key: 'fileName', title: '文件', ellipsis: { tooltip: true } },
  {
    key: 'success',
    title: '状态',
    width: 90,
    render: (row: any) =>
      h(
        NTag,
        { size: 'small', type: row.success ? 'success' : 'error', bordered: false },
        { default: () => (row.success ? '成功' : '失败') },
      ),
  },
  { key: 'message', title: '说明', ellipsis: { tooltip: true } },
];

function renderName(row: any) {
  const button = h(
    NButton,
    {
      text: true,
      type: 'primary',
      style: 'max-width: 100%; justify-content: flex-start',
      onClick: () => openPreview(row),
    },
    {
      default: () =>
        h(
          'span',
          { style: 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap;display:block;max-width:190px' },
          row.name,
        ),
    },
  );
  if (!row.description) return button;
  return h(
    NTooltip,
    { placement: 'top-start' },
    {
      trigger: () => button,
      default: () => row.description,
    },
  );
}

function optionLabel(options: Array<{ label: string; value: string }>, value?: string) {
  return options.find((item) => item.value === value)?.label || value || '-';
}

// ===== 表格列 =====
const columns = [
  {
    key: 'thumbnailPath',
    title: '缩略图',
    width: 80,
    fixed: 'left',
    align: 'center',
    columnType: 'image',
  },
  {
    key: 'name',
    title: '资源名称',
    width: 220,
    fixed: 'left',
    resizable: true,
    render: renderName,
  },
  { key: 'code', title: '编号', width: 190, ellipsis: { tooltip: true } },
  { key: 'culturalRelicNo', title: '文物编号', width: 130, ellipsis: { tooltip: true } },
  {
    key: 'resourceType',
    title: '类型',
    dictType: 'resource_type',
    width: 90,
  },
  { key: 'categoryName', title: '分类', width: 130, ellipsis: { tooltip: true } },
  {
    key: 'fileExt',
    title: '格式',
    width: 80,
    align: 'center',
    render: (row: any) => (row.fileExt ? `.${row.fileExt}` : '-'),
  },
  {
    key: 'status',
    title: '状态',
    dictType: 'resource_status',
    width: 90,
  },
  {
    key: 'flowStatus',
    title: '审核状态',
    width: 100,
    render: (row: any) =>
      h(
        NTag,
        { size: 'small', type: RESOURCE_FLOW_STATUS_TYPE_MAP[row.flowStatus] || 'default', bordered: false },
        { default: () => optionLabel(RESOURCE_FLOW_STATUS_OPTIONS, row.flowStatus) },
      ),
  },
  {
    key: 'validationStatus',
    title: '校验',
    width: 100,
    render: (row: any) =>
      h(
        NTag,
        {
          size: 'small',
          type: RESOURCE_VALIDATION_STATUS_TYPE_MAP[row.validationStatus] || 'default',
          bordered: false,
        },
        { default: () => optionLabel(RESOURCE_VALIDATION_STATUS_OPTIONS, row.validationStatus) },
      ),
  },
  {
    key: 'currentVersion',
    title: '版本',
    width: 70,
    align: 'center',
    render: (row: any) => `v${row.currentVersion || 1}`,
  },
  { key: 'viewCount', title: '浏览', width: 80, align: 'right' },
  { key: 'downloadCount', title: '下载', width: 80, align: 'right' },
  {
    key: 'collectionTime',
    title: '采集时间',
    width: 180,
    columnType: 'dateTime',
  },
  {
    key: 'createTime',
    title: '创建时间',
    width: 180,
    columnType: 'dateTime',
  },
  {
    key: 'actions',
    title: '操作',
    width: 340,
    align: 'center',
    fixed: 'right',
    render: (row: any) =>
      h(
        NSpace,
        { justify: 'center', size: 'small' },
        {
          default: () => [
            h(NButton, { size: 'tiny', text: true, onClick: () => { progressRow.value=row; progressShow.value=true; } }, () => '审批进度'),
            row.canEditData && authStore.hasPermission('RESOURCE_ALL,RESOURCE_AUDIT') ? h(NButton, { size:'tiny', text:true, onClick:()=> { publicationRow.value=row; publicationShow.value=true; } }, () => '渠道发布') : null,

            canEditRow(row)
              ? h(
                  NButton,
                  { size: 'tiny', text: true, type: 'warning', onClick: () => handleEdit(row) },
                  { default: () => '编辑' },
                )
              : null,
            canDeleteRow(row)
              ? h(
                  NButton,
                  { size: 'tiny', text: true, type: 'error', onClick: () => handleConfirmDelete(row) },
                  { default: () => '删除' },
                )
              : null,
            h(
              NButton,
              { size: 'tiny', text: true, type: 'primary', onClick: () => openVersion(row) },
              { default: () => '版本' },
            ),
            h(
              NButton,
              { size: 'tiny', text: true, type: 'primary', onClick: () => openRelation(row) },
              { default: () => '关联' },
            ),
            canSubmitRow(row)
              ? h(
                  NButton,
                  { size: 'tiny', text: true, type: 'primary', onClick: () => handleConfirmSubmit(row) },
                  { default: () => '提交' },
                )
              : null,
            canPublishRow(row)
              ? h(
                  NButton,
                  { size: 'tiny', text: true, type: 'success', onClick: () => handleConfirmPublish(row) },
                  { default: () => '发布' },
                )
              : canArchiveRow(row)
                ? h(
                  NButton,
                  { size: 'tiny', text: true, type: 'warning', onClick: () => handleConfirmArchive(row) },
                  { default: () => '归档' },
                )
                : null,
          ],
        },
      ),
  },
];
</script>

<style scoped>
.resource-management-page {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 12px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.resource-category-sidebar {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.resource-category-sidebar :deep(.n-card-content) {
  display: flex;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.resource-table-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.resource-table-panel :deep(.pageTable) {
  min-width: 0;
}

.category-tree-filter {
  margin-bottom: 8px;
}

.category-all-button {
  margin-bottom: 8px;
  justify-content: flex-start;
}

.resource-category-scroll {
  flex: 1;
  min-height: 0;
}

.batch-upload-drop {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 18px 0;
  color: var(--text-color-2);
}

.batch-upload-hint,
.batch-upload-count {
  font-size: 12px;
  color: var(--text-color-3);
}

.batch-upload-count {
  margin-top: 4px;
}

.batch-upload-result {
  margin-top: 10px;
}

@media (max-width: 960px) {
  .resource-management-page {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
    height: 100%;
    overflow: hidden;
  }

  .resource-category-sidebar {
    height: 180px;
    overflow: hidden;
  }

  .resource-category-sidebar :deep(.n-card-content) {
    overflow: hidden;
  }

  .resource-table-panel {
    overflow: hidden;
  }

  .resource-category-scroll {
    flex: 1;
    min-height: 0;
  }
}
</style>
