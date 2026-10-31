<template>
  <h-page-form
    v-model:show="innerShow"
    :model="form"
    :rules="rules"
    label-width="auto"
    :title="title"
    :ref-table="refTable"
    :disabled="disabled"
    :handle-submit="onSubmit"
  >
    <n-grid :cols="24" :x-gap="12" :y-gap="4">
      <!-- 创建时必选的文件上传 -->
      <n-grid-item v-if="mode === 'add'" :span="24">
        <n-form-item label="上传方式">
          <n-radio-group v-model:value="uploadMode" size="small">
            <n-radio-button v-for="item in uploadModeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </n-radio-button>
          </n-radio-group>
        </n-form-item>
      </n-grid-item>
      <n-grid-item v-if="mode === 'add' && zipPackagePrompt" :span="24">
        <n-alert type="warning" :show-icon="false" class="zip-package-alert">
          <div class="zip-package-alert-content">
            <span>检测到 ZIP 文件。如其中包含模型、贴图或材质，可切换为资源包 ZIP 导入；普通 ZIP 会按单文件保存。</span>
            <n-button size="tiny" type="warning" secondary @click="switchZipToPackage">按资源包导入</n-button>
          </div>
        </n-alert>
      </n-grid-item>
      <n-grid-item v-if="mode === 'add' && compositeResourcePrompt" :span="24">
        <n-alert type="info" :show-icon="false">
          {{ compositeResourcePrompt }}
        </n-alert>
      </n-grid-item>
      <n-grid-item v-if="mode === 'add'" :span="24">
        <n-form-item
          label="资源文件"
          path="file"
          :rule="{ required: true, validator: validateFile, trigger: ['blur', 'change'] }"
        >
          <n-upload
            v-model:file-list="uploadFileList"
            :default-upload="false"
            :multiple="uploadMode === 'packageFiles'"
            :max="uploadMode === 'packageFiles' ? undefined : 1"
            :accept="uploadAccept"
            :directory="uploadMode === 'packageFiles'"
            :directory-dnd="uploadMode === 'packageFiles'"
            :input-props="uploadInputProps"
            :show-retry-button="false"
            :disabled="largeFileUploading"
            @change="onFileChange"
          >
            <n-upload-dragger>
              <div class="py-8">
                <n-icon size="40" depth="3">
                  <IconMdiCloudUpload />
                </n-icon>
                <div class="mt-2">点击或拖拽文件到此处上传</div>
                <div class="mt-1 text-xs text-gray-400">
                  {{ uploadHint }}
                </div>
              </div>
              <div v-if="fileInfo" class="mt-1 text-xs text-gray-500">
                已选择：{{ fileInfo.name }}（{{ formatSize(fileInfo.size) }}）
              </div>
              <div v-if="uploadMode === 'packageFiles' && packageFiles.length" class="mt-1 text-xs text-gray-500">
                已选择：{{ packageFiles.length }} 个文件
              </div>
            </n-upload-dragger>
          </n-upload>
          <div v-if="largeFileUploading" class="resource-upload-progress">
            <n-progress type="line" :percentage="largeFileUploadProgress" processing />
            <div class="mt-1 text-xs text-gray-400">大文件正在分片上传，请勿关闭当前页面</div>
          </div>
        </n-form-item>
      </n-grid-item>
      <n-grid-item
        v-if="mode === 'add' && uploadMode !== 'file' && (packageInspectLoading || packageInspectFiles.length || packageInspectIssues.length || packageInspectAuditSummary)"
        :span="24"
      >
        <n-form-item label="关联文件">
          <n-spin :show="packageInspectLoading" class="package-file-spin">
            <div class="package-file-panel">
              <n-alert v-if="packageInspectIssues.length" type="warning" :show-icon="false" class="mb-2">
                {{ packageInspectIssues.join('；') }}
              </n-alert>
              <div class="package-file-summary">
                <span v-if="packageInspectAuditSummary">
                  {{ packageInspectAuditSummary.packageTypeLabel || '复合资源包' }}：
                  {{ packageInspectAuditSummary.fileCount || packageInspectFiles.length }} 个文件，
                  {{ formatSize(Number(packageInspectAuditSummary.totalSize || 0)) }}
                </span>
                <span v-else>已识别 {{ packageInspectFiles.length }} 个包内文件</span>
                <span v-if="selectedMainRelativePath">主文件：{{ selectedMainRelativePath }}</span>
                <span v-else class="text-orange-500">请选择主文件</span>
              </div>
              <n-radio-group v-model:value="selectedMainRelativePath" class="package-file-list">
                <div
                  v-for="item in visiblePackageInspectFiles"
                  :key="item.relativePath"
                  class="package-file-row"
                  :class="{ 'is-main': selectedMainRelativePath === item.relativePath }"
                >
                  <n-radio :value="item.relativePath" :disabled="!item.mainCandidate" />
                  <div class="package-file-info">
                    <div class="package-file-path">{{ item.relativePath }}</div>
                    <div class="package-file-meta">
                      <n-tag size="tiny" :bordered="false" :type="item.mainCandidate ? 'success' : 'default'">
                        {{ item.mainCandidate ? '主文件候选' : (item.role || 'AUX') }}
                      </n-tag>
                      <span>{{ item.fileExt || '-' }}</span>
                      <span>{{ formatSize(Number(item.fileSize || 0)) }}</span>
                    </div>
                  </div>
                </div>
              </n-radio-group>
              <div v-if="packageInspectFiles.length > visiblePackageInspectFiles.length" class="package-file-truncated">
                包内共有 {{ packageInspectFiles.length }} 个文件，为保证页面流畅仅展示主文件候选和前
                {{ visiblePackageInspectFiles.length }} 项；其余文件仍会完整入库并参与自动校验。
              </div>
            </div>
          </n-spin>
        </n-form-item>
      </n-grid-item>
      <n-grid-item :span="24">
        <div class="flex gap-3 items-start">
          <div class="flex-1 min-w-0">
            <n-form-item label="资源名称" path="name">
              <n-input v-model:value="form.name" placeholder="请输入资源名称" clearable />
            </n-form-item>
            <n-form-item label="资源编号" path="code">
              <n-input v-model:value="form.code" placeholder="留空自动生成，如 MY--BWG--SW--2026060001" clearable />
            </n-form-item>
            <n-form-item label="文物编号" path="culturalRelicNo">
              <n-input v-model:value="form.culturalRelicNo" placeholder="请输入关联文物编号" clearable />
            </n-form-item>
            <n-form-item label="分类" path="categoryId">
              <CategoryTreeSelect v-model:value="form.categoryId" />
            </n-form-item>
          </div>
          <div class="w-[140px] flex-shrink-0">
            <n-form-item label="缩略图" path="thumbnailPath" label-placement="top">
              <HAvatar v-model:value="form.thumbnailPath" />
            </n-form-item>
          </div>
        </div>
      </n-grid-item>

      <n-grid-item :span="12"><n-form-item label="文物年代"><n-input v-model:value="form.era" :disabled="disabled" maxlength="100" placeholder="如清代，与采集日期分开登记" /></n-form-item></n-grid-item>
      <n-grid-item :span="12"><n-form-item label="资源用途"><n-select v-model:value="form.purpose" :options="PURPOSES" :disabled="disabled" clearable /></n-form-item></n-grid-item>
      <n-grid-item :span="24"><n-form-item label="关联在册藏品"><ResourceCollectionSelect v-model="form.collectionId" :label="form.culturalRelicNo" :disabled="disabled" @select="value => { if (value) { form.culturalRelicNo = value.number; if (!form.era) form.era = value.era; } }" /></n-form-item></n-grid-item>
      <n-grid-item v-if="form.resourceType === 'PANORAMA'" :span="24"><n-alert type="info">VR全景请上传宽高比2:1的等距柱状投影图片（JPG或PNG），可在资源预览中环视。</n-alert></n-grid-item>
      <n-grid-item :span="8">
        <n-form-item label="资源类型" path="resourceType">
          <n-select
            v-model:value="form.resourceType"
            :options="RESOURCE_TYPE_OPTIONS"
            placeholder="留空则按扩展名自动推断"
            clearable
          />
        </n-form-item>
      </n-grid-item>
      <n-grid-item :span="8">
        <n-form-item label="预览类型" path="previewType">
          <n-select
            v-model:value="form.previewType"
            :options="RESOURCE_PREVIEW_TYPE_OPTIONS"
            placeholder="请选择预览类型"
          />
        </n-form-item>
      </n-grid-item>
      <n-grid-item :span="8">
        <n-form-item label="采集时间" path="collectionTime">
          <n-date-picker v-model:value="form.collectionTime" type="datetime" clearable style="width: 100%" />
        </n-form-item>
      </n-grid-item>
      <n-grid-item :span="8">
        <n-form-item label="权限等级" path="permissionLevel">
          <n-select
            v-model:value="form.permissionLevel"
            :options="RESOURCE_PERMISSION_LEVEL_OPTIONS"
            placeholder="请选择权限等级"
          />
        </n-form-item>
      </n-grid-item>
      <n-grid-item v-if="supportsPreviewConfig" :span="8">
        <n-form-item label="最佳展示位置">
          <n-switch v-model:value="customPreviewView" :disabled="disabled">
            <template #checked>已配置</template>
            <template #unchecked>自动取景</template>
          </n-switch>
        </n-form-item>
      </n-grid-item>
      <n-grid-item v-if="supportsPreviewConfig && customPreviewView" :span="24">
        <div class="preview-config-panel">
          <div v-for="field in cameraVectorFields" :key="field.key" class="preview-vector-field">
            <span class="preview-config-label">{{ field.label }}</span>
            <n-input-number
              v-for="axis in 3"
              :key="axis"
              v-model:value="form.previewConfig[field.key][axis - 1]"
              :show-button="false"
              :placeholder="['X', 'Y', 'Z'][axis - 1]"
              :disabled="disabled"
            />
          </div>
          <div class="preview-scalar-field">
            <span class="preview-config-label">视野角</span>
            <n-input-number v-model:value="form.previewConfig.cameraFov" :min="1" :max="179" :disabled="disabled" />
          </div>
          <div class="preview-scalar-field">
            <span class="preview-config-label">背景色</span>
            <n-color-picker v-model:value="form.previewConfig.backgroundColor" :show-alpha="false" :disabled="disabled" />
          </div>
        </div>
      </n-grid-item>
      <n-grid-item :span="8">
        <n-form-item label="可见范围" path="visibility">
          <n-select
            v-model:value="form.visibility"
            :options="RESOURCE_VISIBILITY_OPTIONS"
            placeholder="请选择可见范围"
          />
        </n-form-item>
      </n-grid-item>
      <n-grid-item :span="8">
        <n-form-item label="排序" path="sort">
          <n-input-number v-model:value="form.sort" :min="0" style="width: 100%" />
        </n-form-item>
      </n-grid-item>
      <n-grid-item v-if="form.validationStatus" :span="8">
        <n-form-item label="校验状态" path="validationStatus">
          <n-input :value="form.validationStatus" disabled />
        </n-form-item>
      </n-grid-item>

      <n-grid-item :span="24">
        <n-form-item label="描述" path="description">
          <n-input
            v-model:value="form.description"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            placeholder="请输入描述"
          />
        </n-form-item>
      </n-grid-item>

      <n-grid-item :span="24">
        <n-form-item label="标签" path="tagsArr">
          <div class="w-full">
            <n-dynamic-tags v-model:value="tagsArr" />
            <div v-if="topTagsList.length" class="mt-2 flex flex-wrap gap-2 items-center">
              <span class="text-xs text-gray-400">热门：</span>
              <n-tag
                v-for="t in topTagsList"
                :key="t.id"
                size="small"
                :bordered="false"
                style="cursor: pointer"
                @click="addTag(t.name)"
                >{{ t.name }}<span class="ml-1 text-gray-400">({{ t.useCount }})</span></n-tag
              >
            </div>
          </div>
        </n-form-item>
      </n-grid-item>

      <n-grid-item :span="24"><n-form-item label="元数据"><ResourceMetadataFields v-model="form.metadata" :category-id="form.categoryId" :resource-type="form.resourceType" :disabled="disabled" :create="mode === 'add'" /></n-form-item></n-grid-item>


      <!-- 扩展表单插槽，默认为空，外部可注入额外字段 -->
      <n-grid-item :span="24">
        <slot name="extendForm" :form="form"></slot>
      </n-grid-item>
    </n-grid>
  </h-page-form>
</template>

<script lang="ts" setup>
import { ref, computed, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useMessage } from 'naive-ui';
import {
  add,
  edit,
  inspectPackage,
  inspectPackageFiles,
  packageAdd,
  packageFilesAdd,
  topTags,
  RESOURCE_PERMISSION_LEVEL_OPTIONS,
  RESOURCE_VISIBILITY_OPTIONS,
  RESOURCE_TYPE_OPTIONS,
  RESOURCE_PREVIEW_TYPE_OPTIONS,
} from '../api/resource';
import { formatSize } from '../api/stat';
import ResourceMetadataFields from './ResourceMetadataFields.vue';
import ResourceCollectionSelect from './ResourceCollectionSelect.vue';
import { PURPOSES } from '../api/business';
import CategoryTreeSelect from './CategoryTreeSelect.vue';

const props = defineProps({
  show: { type: Boolean, default: false },
  mode: { type: String as () => 'add' | 'edit' | 'view', default: 'add' },
  title: { type: String, default: '' },
  initial: { type: Object, default: () => ({}) },
  refTable: { type: Object, default: null },
});

const emit = defineEmits(['update:show', 'success']);

const message = useMessage();

const defaultForm = {
  id: '',
  name: '',
  code: '',
  culturalRelicNo: '',
  categoryId: '',
  resourceType: '',
  previewType: 'AUTO',
  previewConfig: {
    cameraPosition: [5, 5, 5],
    cameraTarget: [0, 0, 0],
    cameraUp: [0, 1, 0],
    cameraFov: 45,
    backgroundColor: '#111827',
  },
  collectionTime: null,
  thumbnailPath: '',
  description: '',
  tags: '',
  metadata: '', era: '', purpose: null, collectionId: '',
  permissionLevel: 'LEVEL_1',
  visibility: 'GROUP',
  validationStatus: '',
  sort: 0,
};

const form = ref<Record<string, any>>(cloneDeep(defaultForm));
const tagsArr = ref<string[]>([]);
const fileInfo = ref<{ name: string; size: number; raw: File } | null>(null);
const uploadFileList = ref<any[]>([]);
const largeFileUploading = ref(false);
const largeFileUploadProgress = ref(0);
const uploadMode = ref<'file' | 'package' | 'packageFiles'>('file');
const packageFiles = ref<File[]>([]);
const packageRelativePaths = ref<string[]>([]);
const packageInspectLoading = ref(false);
const packageInspectFiles = ref<any[]>([]);
const packageInspectIssues = ref<string[]>([]);
const packageInspectAuditSummary = ref<any>(null);
const selectedMainRelativePath = ref('');
const zipPackagePrompt = ref(false);
const customPreviewView = ref(false);
let preserveCurrentSelectionOnModeChange = false;
const topTagsList = ref<any[]>([]);

const uploadModeOptions = [
  { label: '普通文件', value: 'file' },
  { label: '资源包 ZIP', value: 'package' },
  { label: '资源包文件夹', value: 'packageFiles' },
];

const innerShow = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
});

const disabled = computed(() => props.mode === 'view');
const metadataEntries = computed(() => parseMetadataEntries(form.value.metadata));

/**
 * 3D Tiles 的 tileset.json 是明确的复合资源入口。
 * meta.json 是通用文件名，不在前端按名称误判；后端会按 SOG v2 内容契约校验。
 */
const PACKAGE_ENTRY_LABELS: Record<string, string | undefined> = {
  'tileset.json': '3D Tiles',
  'lod-meta.json': 'Streamed SOG',
};

const cameraVectorFields = [
  { label: '相机位置', key: 'cameraPosition' },
  { label: '观察目标', key: 'cameraTarget' },
  { label: '向上方向', key: 'cameraUp' },
];
const supportsPreviewConfig = computed(() =>
  ['AUTO', 'MODEL_3D', 'POINT_CLOUD', 'TILES_3D', 'GAUSSIAN_SPLAT_PLY', 'GAUSSIAN_SPLAT_SOG', 'GAUSSIAN_SPLAT_SOG_LOD']
    .includes(form.value.previewType),
);

function packageEntryLabel(fileName?: string) {
  return fileName ? PACKAGE_ENTRY_LABELS[fileName.toLowerCase()] : undefined;
}

const compositeResourcePrompt = computed(() => {
  if (uploadMode.value !== 'file' || !fileInfo.value?.name) return '';
  const fileName = fileInfo.value.name.toLowerCase();
  const entryLabel = packageEntryLabel(fileName);
  if (entryLabel) {
    return `${entryLabel} 不能只上传 ${fileName}，请切换“资源包文件夹”并选择完整目录，系统将生成一条资源和一张审核单。`;
  }
  const ext = fileName.includes('.') ? fileName.split('.').pop() : '';
  if (['gltf', 'obj', 'osgb', 'dae', 'fbx', '3ds'].includes(ext || '')) {
    return '该格式可能依赖贴图、材质或分层文件；如存在配套文件，请改用资源包 ZIP 或资源包文件夹整体入库。';
  }
  return '';
});
const visiblePackageInspectFiles = computed(() => {
  const prioritized = [
    ...packageInspectFiles.value.filter((item: any) => item.relativePath === selectedMainRelativePath.value),
    ...packageInspectFiles.value.filter((item: any) => item.mainCandidate),
    ...packageInspectFiles.value,
  ];
  const seen = new Set<string>();
  return prioritized.filter((item: any) => {
    const path = String(item.relativePath || '');
    if (!path || seen.has(path)) return false;
    seen.add(path);
    return true;
  }).slice(0, 200);
});
const uploadAccept = computed(() =>
  uploadMode.value === 'package' ? '.zip,application/zip,application/x-zip-compressed' : undefined,
);
const uploadInputProps = computed<any>(() =>
  uploadMode.value === 'packageFiles' ? { webkitdirectory: '' } : undefined,
);
const uploadHint = computed(() =>
  ({
    file: '支持 wps/word/pdf/caj/xls/xlsx/ppt、mview/osgb/stl/obj/ply/sog/raw/tif 等资源文件',
    package: '适用于 3D Tiles、SOG、glTF、OBJ、OSGB 等复合资源；整个ZIP生成一条资源和一张审核单',
    packageFiles: '适用于已解开的 3D Tiles / SOG 等目录；全部成员只作为一个资源包审核',
  })[uploadMode.value],
);

const rules = {
  name: { required: true, message: '请输入资源名称', trigger: ['blur', 'input'] },
  // code 留空时后端会按 MY--BWG--类型--年月序号自动生成
};

// 监听外部传入的初始值 -> 填充表单
watch(
  () => [props.show, props.initial],
  ([show, initial]) => {
    if (show) {
      const init: any = initial || {};
      form.value = {
        ...cloneDeep(defaultForm),
        ...cloneDeep(init),
        previewType: init.previewType || 'AUTO',
        previewConfig: previewFormConfig(init.previewConfig),
      };
      customPreviewView.value = Boolean(init.previewConfig);
      tagsArr.value = (init.tags || '')
        .split(',')
        .map((s: string) => s.trim())
        .filter(Boolean);
      fileInfo.value = null;
      uploadFileList.value = [];
      largeFileUploading.value = false;
      largeFileUploadProgress.value = 0;
      uploadMode.value = 'file';
      packageFiles.value = [];
      packageRelativePaths.value = [];
      resetPackageInspect();
      zipPackagePrompt.value = false;
      loadTopTags();
    }
  },
  { immediate: true, deep: true },
);

watch(
  tagsArr,
  (arr) => {
    form.value.tags = (arr || []).join(',');
  },
  { deep: true },
);

watch(uploadMode, () => {
  zipPackagePrompt.value = false;
  if (preserveCurrentSelectionOnModeChange) {
    preserveCurrentSelectionOnModeChange = false;
    return;
  }
  fileInfo.value = null;
  uploadFileList.value = [];
  packageFiles.value = [];
  packageRelativePaths.value = [];
  resetPackageInspect();
});

function addTag(name: string) {
  if (!tagsArr.value.includes(name)) {
    tagsArr.value = [...tagsArr.value, name];
  }
}

function previewFormConfig(input: any) {
  const defaults = cloneDeep(defaultForm.previewConfig);
  if (!input || typeof input !== 'object') return defaults;
  return {
    ...defaults,
    ...cloneDeep(input),
    cameraPosition: Array.isArray(input.cameraPosition) && input.cameraPosition.length === 3 ? [...input.cameraPosition] : defaults.cameraPosition,
    cameraTarget: Array.isArray(input.cameraTarget) && input.cameraTarget.length === 3 ? [...input.cameraTarget] : defaults.cameraTarget,
    cameraUp: Array.isArray(input.cameraUp) && input.cameraUp.length === 3 ? [...input.cameraUp] : defaults.cameraUp,
  };
}

async function loadTopTags() {
  try {
    const resp: any = await topTags(10);
    topTagsList.value = resp.data || [];
  } catch {
    topTagsList.value = [];
  }
}

function onFileChange({ fileList }: any) {
  const nextFileList = uploadMode.value === 'packageFiles' ? fileList || [] : (fileList || []).slice(-1);
  uploadFileList.value = nextFileList;
  zipPackagePrompt.value = false;
  if (uploadMode.value === 'packageFiles') {
    const entries = nextFileList
      .map((item: any) => ({ file: item.file, relativePath: extractRelativePath(item) }))
      .filter((item: any) => item.file);
    packageFiles.value = entries.map((item: any) => item.file);
    packageRelativePaths.value = entries.map((item: any) => item.relativePath);
    fileInfo.value = null;
    if (!form.value.name && packageRelativePaths.value.length) {
      form.value.name = inferPackageName(packageRelativePaths.value);
    }
    void inspectSelectedPackage();
    return;
  }

  packageFiles.value = [];
  packageRelativePaths.value = [];
  const last = nextFileList[nextFileList.length - 1];
  if (last && last.file) {
    fileInfo.value = { name: last.name, size: last.file.size, raw: last.file };
    if (!form.value.name) {
      form.value.name = last.name.replace(/\.[^.]+$/, '');
    }
    maybePromptZipPackage(last.file);
    if (uploadMode.value === 'package') {
      void inspectSelectedPackage();
    } else {
      if (form.value.previewType === 'AUTO' && last.name.toLowerCase().endsWith('.sog')) {
        form.value.previewType = 'GAUSSIAN_SPLAT_SOG';
      }
      resetPackageInspect();
    }
  } else {
    fileInfo.value = null;
    resetPackageInspect();
  }
}

function validateFile() {
  if (props.mode === 'add' && uploadMode.value === 'packageFiles' && packageFiles.value.length === 0) {
    return new Error('请选择资源包文件夹');
  }
  if (props.mode === 'add' && uploadMode.value !== 'packageFiles' && !fileInfo.value) {
    return new Error('请选择要上传的文件');
  }
  if (props.mode === 'add' && uploadMode.value === 'file' && packageEntryLabel(fileInfo.value?.name)) {
    return new Error(`${packageEntryLabel(fileInfo.value?.name)} 请选择完整资源包文件夹`);
  }
  return true;
}

function maybePromptZipPackage(file: File) {
  zipPackagePrompt.value = uploadMode.value === 'file' && isZipFile(file);
}

function isZipFile(file: File) {
  const name = file.name || '';
  return name.toLowerCase().endsWith('.zip') || file.type === 'application/zip' || file.type === 'application/x-zip-compressed';
}

function switchZipToPackage() {
  if (!fileInfo.value) return;
  preserveCurrentSelectionOnModeChange = true;
  uploadMode.value = 'package';
  zipPackagePrompt.value = false;
  void inspectSelectedPackage();
}

async function inspectSelectedPackage() {
  if (uploadMode.value === 'package' && !fileInfo.value?.raw) {
    resetPackageInspect();
    return;
  }
  if (uploadMode.value === 'packageFiles' && packageFiles.value.length === 0) {
    resetPackageInspect();
    return;
  }
  packageInspectLoading.value = true;
  try {
    const resp: any =
      uploadMode.value === 'package'
        ? await inspectPackage(fileInfo.value!.raw)
        : await inspectPackageFiles(packageFiles.value, packageRelativePaths.value);
    applyPackageInspect(resp?.data || {});
  } catch (error: any) {
    packageInspectFiles.value = [];
    packageInspectAuditSummary.value = null;
    selectedMainRelativePath.value = '';
    packageInspectIssues.value = [error?.message || '资源包文件检查失败'];
  } finally {
    packageInspectLoading.value = false;
  }
}

function applyPackageInspect(result: any) {
  packageInspectFiles.value = result.files || [];
  packageInspectIssues.value = result.issues || [];
  packageInspectAuditSummary.value = result.auditSummary || null;
  selectedMainRelativePath.value = result.suggestedMainRelativePath || '';
  if (form.value.previewType === 'AUTO') {
    if (result.auditSummary?.packageType === 'TILES_3D') form.value.previewType = 'TILES_3D';
    if (result.auditSummary?.packageType === 'SOG') form.value.previewType = 'GAUSSIAN_SPLAT_SOG';
    if (result.auditSummary?.packageType === 'STREAMED_SOG') form.value.previewType = 'GAUSSIAN_SPLAT_SOG_LOD';
  }
}

function resetPackageInspect() {
  packageInspectLoading.value = false;
  packageInspectFiles.value = [];
  packageInspectIssues.value = [];
  packageInspectAuditSummary.value = null;
  selectedMainRelativePath.value = '';
}

function extractRelativePath(item: any) {
  const file = item?.file || {};
  const path = item?.fullPath || file.webkitRelativePath || item?.name || file.name || '';
  return String(path).replace(/^\/+/, '');
}

function inferPackageName(paths: string[]) {
  const roots = Array.from(
    new Set(
      paths
        .map((path) => path.split('/')[0])
        .filter(Boolean),
    ),
  );
  if (roots.length === 1 && paths.some((path) => path.includes('/'))) return roots[0];
  return (paths.find((path) => /\.[^/.]+$/.test(path)) || paths[0] || '未命名资源').replace(/.*\//, '').replace(/\.[^.]+$/, '');
}

function parseMetadataEntries(input: any) {
  if (!input) return [];
  try {
    const metadata = typeof input === 'string' ? JSON.parse(input) : input;
    if (!metadata || typeof metadata !== 'object') {
      return [{ key: 'value', label: '值', value: formatMetadataValue(metadata) }];
    }
    const entries = Array.isArray(metadata)
      ? metadata.map((value, index) => [`${index + 1}`, value])
      : Object.entries(metadata);
    return entries
      .filter(([, value]) => value !== null && value !== undefined && value !== '')
      .map(([key, value]) => ({
        key,
        label: String(key),
        value: formatMetadataValue(value),
      }));
  } catch {
    return [];
  }
}

function formatMetadataValue(value: any): string {
  if (Array.isArray(value)) return value.map(formatMetadataValue).join('、');
  if (value && typeof value === 'object') return JSON.stringify(value);
  return String(value ?? '-');
}

async function onSubmit() {
  // 元数据 JSON 校验
  if (form.value.metadata && form.value.metadata.trim()) {
    try {
      JSON.parse(form.value.metadata);
    } catch {
      message.error('扩展元数据不是合法的 JSON');
      return Promise.reject(new Error('扩展元数据不是合法的 JSON'));
    }
  }

  if (props.mode === 'add') {
    if (uploadMode.value === 'packageFiles' && packageFiles.value.length === 0) {
      message.error('请选择资源包文件夹');
      return Promise.reject(new Error('请选择资源包文件夹'));
    }
    if (uploadMode.value !== 'packageFiles' && !fileInfo.value) {
      message.error('请选择要上传的文件');
      return Promise.reject(new Error('请选择要上传的文件'));
    }
    if (uploadMode.value === 'file' && packageEntryLabel(fileInfo.value?.name)) {
      message.error(`${packageEntryLabel(fileInfo.value?.name)} 请切换“资源包文件夹”并选择完整目录`);
      return Promise.reject(new Error('请选择完整资源包文件夹'));
    }
    if (uploadMode.value === 'package' && fileInfo.value && !fileInfo.value.name.toLowerCase().endsWith('.zip')) {
      message.error('资源包模式请选择ZIP文件');
      return Promise.reject(new Error('资源包模式请选择ZIP文件'));
    }
    if (uploadMode.value !== 'file' && packageInspectLoading.value) {
      message.error('资源包文件仍在检查中，请稍后提交');
      return Promise.reject(new Error('资源包文件仍在检查中，请稍后提交'));
    }
    if (uploadMode.value !== 'file' && !selectedMainRelativePath.value) {
      message.error('请选择资源包主文件');
      return Promise.reject(new Error('请选择资源包主文件'));
    }
    const vo: Record<string, any> = {
      ...form.value,
      purpose: form.value.purpose || '',
      previewConfig: supportsPreviewConfig.value && customPreviewView.value ? form.value.previewConfig : null,
    };
    if (uploadMode.value !== 'file') {
      vo.mainRelativePath = selectedMainRelativePath.value;
    }
    delete vo.id;
    delete vo.validationStatus;
    let resp: any;
    try {
      resp =
        uploadMode.value === 'packageFiles'
          ? await packageFilesAdd(packageFiles.value, packageRelativePaths.value, vo)
          : uploadMode.value === 'package'
            ? await packageAdd(fileInfo.value!.raw, vo)
            : await add(fileInfo.value!.raw, vo, (percentage) => {
                largeFileUploading.value = true;
                largeFileUploadProgress.value = percentage;
              });
    } finally {
      largeFileUploading.value = false;
    }
    const detail = resp?.data || {};
    if (detail.files?.length) {
      message.success(`资源包上传成功，共 ${detail.files.length} 个文件`, { duration: 6000 });
    } else if (detail.filePath) {
      message.success(`上传成功，文件路径：${detail.filePath}`, { duration: 6000 });
    }
    emit('success', resp);
    return resp;
  } else {
    const resp = await edit({
      ...form.value,
      purpose: form.value.purpose || '',
      previewConfig: supportsPreviewConfig.value && customPreviewView.value ? form.value.previewConfig : null,
    });
    emit('success', resp);
    return resp;
  }
}

defineExpose({ reloadTopTags: loadTopTags });
</script>

<style scoped>
.metadata-field-list {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
}
.resource-upload-progress {
  width: 100%;
  margin-top: 10px;
}
.metadata-field {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
  background: var(--card-color, #fff);
}
.metadata-label {
  display: block;
  margin-bottom: 4px;
  color: #888;
  font-size: 12px;
}
.metadata-value {
  display: block;
  word-break: break-word;
  color: #333;
  font-size: 13px;
  line-height: 1.5;
}
.metadata-empty {
  width: 100%;
  padding: 10px 0;
}
.package-file-spin {
  width: 100%;
}
.package-file-panel {
  width: 100%;
}
.package-file-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #666;
  font-size: 12px;
}
.package-file-list {
  display: grid;
  max-height: 240px;
  overflow: auto;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
}
.package-file-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  padding: 8px 10px;
  border-bottom: 1px solid var(--n-border-color, #efeff5);
}
.package-file-row:last-child {
  border-bottom: 0;
}
.package-file-row.is-main {
  background: rgba(24, 160, 88, 0.08);
}
.package-file-info {
  min-width: 0;
  flex: 1;
}
.package-file-path {
  word-break: break-all;
  color: #333;
  font-size: 12px;
  line-height: 1.5;
}
.package-file-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
  color: #999;
  font-size: 12px;
}
.package-file-truncated {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
  line-height: 1.5;
}
.zip-package-alert {
  width: 100%;
}
.zip-package-alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  line-height: 1.5;
}
.preview-config-panel {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--n-border-color, #efeff5);
  border-radius: 6px;
}
.preview-vector-field {
  display: grid;
  grid-template-columns: 76px repeat(3, minmax(0, 1fr));
  gap: 8px;
  align-items: center;
  grid-column: 1 / -1;
}
.preview-scalar-field {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}
.preview-config-label {
  color: #666;
  font-size: 12px;
}
@media (max-width: 640px) {
  .zip-package-alert-content {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
