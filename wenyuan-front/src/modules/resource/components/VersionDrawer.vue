<template>
  <n-drawer v-model:show="innerShow" :width="720" placement="right">
    <n-drawer-content :title="`版本管理 - ${resourceName || ''}`" closable>
      <div class="flex items-center justify-between mb-3">
        <span class="text-sm text-gray-500">
          当前共 {{ versions.length }} 个版本
        </span>
        <n-upload
          v-if="editable"
          :default-upload="false"
          :max="1"
          :show-file-list="false"
          @change="onUploadChange"
        >
          <n-button type="primary" :disabled="!resourceId">
            <template #icon>
              <n-icon><IconMdiCloudUpload /></n-icon>
            </template>
            上传新版本
          </n-button>
        </n-upload>
      </div>

      <n-modal
        v-model:show="showChangeLogModal"
        preset="dialog"
        title="变更说明"
        positive-text="确认上传"
        negative-text="取消"
        @positive-click="confirmUpload"
      >
        <div v-if="pendingFile" class="mb-2 text-sm text-gray-500">
          文件：{{ pendingFile.name }}
        </div>
        <n-input
          v-model:value="changeLog"
          type="textarea"
          placeholder="请输入本次版本变更说明"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
      </n-modal>

      <n-data-table
        :columns="columns"
        :data="versions"
        :loading="loading"
        size="small"
        :bordered="false"
      />

      <n-modal v-model:show="compareShow" preset="card" style="width: 920px" title="版本对比">
        <n-grid :cols="2" :x-gap="16">
          <n-grid-item>
            <div class="text-sm font-medium mb-2">当前版本 v{{ currentCompare?.versionNumber || currentVersion }}</div>
            <div class="compare-box">
              <n-image
                v-if="isImage(currentCompare?.filePath)"
                :src="fileUrl(currentCompare.filePath)"
                width="100%"
                object-fit="contain"
              />
              <div v-else class="text-sm text-gray-500">
                <div>路径：{{ currentCompare?.filePath || '-' }}</div>
                <div>大小：{{ formatSize(currentCompare?.fileSize || 0) }}</div>
                <div>MD5：{{ currentCompare?.fileHash || '-' }}</div>
              </div>
            </div>
          </n-grid-item>
          <n-grid-item>
            <div class="text-sm font-medium mb-2">对比版本 v{{ compareTarget?.versionNumber || '-' }}</div>
            <div class="compare-box">
              <n-image
                v-if="isImage(compareTarget?.filePath)"
                :src="fileUrl(compareTarget.filePath)"
                width="100%"
                object-fit="contain"
              />
              <div v-else class="text-sm text-gray-500">
                <div>路径：{{ compareTarget?.filePath || '-' }}</div>
                <div>大小：{{ formatSize(compareTarget?.fileSize || 0) }}</div>
                <div>MD5：{{ compareTarget?.fileHash || '-' }}</div>
              </div>
            </div>
          </n-grid-item>
        </n-grid>
      </n-modal>
    </n-drawer-content>
  </n-drawer>
</template>

<script lang="ts" setup>
import { h, ref, computed, watch } from 'vue';
import { NButton, NSpace, NTag, useMessage, useDialog } from 'naive-ui';
import dayjs from 'dayjs';
import {
  versionList,
  uploadVersion,
  rollback,
  deleteVersion,
  RESOURCE_FLOW_STATUS_OPTIONS,
  RESOURCE_FLOW_STATUS_TYPE_MAP,
} from '../api/resource';
import { formatSize } from '../api/stat';
import { fileUrl } from '@/utils';

const props = defineProps({
  show: { type: Boolean, default: false },
  resourceId: { type: String, default: '' },
  resourceName: { type: String, default: '' },
  currentVersion: { type: Number, default: 1 },
  editable: { type: Boolean, default: true },
});

const emit = defineEmits(['update:show', 'changed']);

const message = useMessage();
const dialog = useDialog();

const innerShow = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
});

const versions = ref<any[]>([]);
const loading = ref(false);

const pendingFile = ref<File | null>(null);
const showChangeLogModal = ref(false);
const changeLog = ref('');
const compareShow = ref(false);
const compareTarget = ref<any>(null);
const currentCompare = computed(() => versions.value.find((item) => item.versionNumber === props.currentVersion));

watch(
  () => [props.show, props.resourceId],
  ([show, id]) => {
    if (show && id) {
      loadVersions();
    }
  },
);

async function loadVersions() {
  if (!props.resourceId) return;
  loading.value = true;
  try {
    const resp: any = await versionList(props.resourceId);
    versions.value = resp.data || [];
  } finally {
    loading.value = false;
  }
}

function onUploadChange({ fileList }: any) {
  if (!props.editable) {
    return;
  }
  const last = fileList[fileList.length - 1];
  if (last && last.file) {
    pendingFile.value = last.file;
    changeLog.value = '';
    showChangeLogModal.value = true;
  }
}

async function confirmUpload() {
  if (!props.editable) {
    return;
  }
  if (!pendingFile.value || !props.resourceId) return;
  try {
    await uploadVersion(props.resourceId, pendingFile.value, changeLog.value);
    message.success('新版本已提交审核，审核通过后生效');
    pendingFile.value = null;
    await loadVersions();
    emit('changed');
  } catch (e: any) {
    message.error(e?.message || '上传失败');
  }
}

function doRollback(row: any) {
  if (!props.editable) {
    return;
  }
  dialog.warning({
    title: '回滚确认',
    content: `确定回滚到版本 v${row.versionNumber}？当前版本将被替换。`,
    positiveText: '确认回滚',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await rollback(props.resourceId, row.versionNumber);
        message.success(`已回滚到 v${row.versionNumber}`);
        await loadVersions();
        emit('changed');
      } catch (e: any) {
        message.error(e?.message || '回滚失败');
      }
    },
  });
}

function doDelete(row: any) {
  if (!props.editable || row.flowStatus === 'PASS') {
    return;
  }
  dialog.warning({
    title: '删除版本',
    content: `确定删除版本 v${row.versionNumber}？删除后不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteVersion(props.resourceId, row.versionNumber);
        message.success(`已删除 v${row.versionNumber}`);
        await loadVersions();
        emit('changed');
      } catch (e: any) {
        message.error(e?.message || '删除失败');
      }
    },
  });
}

function openCompare(row: any) {
  compareTarget.value = row;
  compareShow.value = true;
}

function isImage(path?: string) {
  if (!path) return false;
  return /\.(png|jpe?g|gif|webp|bmp|tiff?|svg)$/i.test(path);
}

const columns = [
  {
    key: 'versionNumber',
    title: '版本号',
    width: 90,
    render: (row: any) =>
      h(
        NTag,
        {
          size: 'small',
          type: row.versionNumber === props.currentVersion ? 'success' : 'default',
          bordered: false,
        },
        { default: () => `v${row.versionNumber}` },
      ),
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
    key: 'fileSize',
    title: '文件大小',
    width: 110,
    render: (row: any) => formatSize(row.fileSize || 0),
  },
  { key: 'fileHash', title: 'MD5', width: 220, ellipsis: { tooltip: true } },
  { key: 'changeLog', title: '变更说明', ellipsis: { tooltip: true } },
  {
    key: 'createTime',
    title: '创建时间',
    width: 160,
    render: (row: any) => (row.createTime ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm') : '-'),
  },
  {
    key: 'actions',
    title: '操作',
    width: 130,
    align: 'center' as const,
    render: (row: any) =>
      row.versionNumber === props.currentVersion
        ? h(NTag, { size: 'small', type: 'success' }, { default: () => '当前' })
        : h(
            NSpace,
            { justify: 'center', size: 'small' },
            {
              default: () => [
                h(
                  NButton,
                  { text: true, type: 'primary', size: 'small', onClick: () => openCompare(row) },
                  { default: () => '对比' },
                ),
                props.editable && row.flowStatus === 'PASS'
                  ? h(
                      NButton,
                      { text: true, type: 'primary', size: 'small', onClick: () => doRollback(row) },
                      { default: () => '回滚' },
                    )
                  : null,
                props.editable && row.flowStatus !== 'PASS'
                  ? h(
                      NButton,
                      { text: true, type: 'error', size: 'small', onClick: () => doDelete(row) },
                      { default: () => '删除' },
                    )
                  : null,
              ],
            },
          ),
  },
];

function optionLabel(options: Array<{ label: string; value: string }>, value?: string) {
  return options.find((item) => item.value === value)?.label || value || '-';
}
</script>

<style scoped>
.compare-box {
  min-height: 260px;
  max-height: 520px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--n-border-color);
  border-radius: 6px;
  background: var(--n-color);
}
</style>
