<template>
  <section class="original-history-workspace">
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
    <HPageDataTable auto-scroll-x
      :key="tableVersion"
      ref="tableRef"
      class="original-history-table"
      :columns="columns"
      :data="items"
      :fetch-data="fetchHistory"
      :params="queryParams"
      :init-query="false"
      :show-header="false"
      :show-toolbar="false"
      :is-add="false"
      :is-edit="false"
      :is-copy="false"
      :is-delete="false"
      :is-export="false"
      :scroll-x="1290"
      row-key-field="id"
    >
      <template #queryForm>
        <NFormItem v-if="!businessType" label="业务">
          <NSelect
            v-model:value="selectedType"
            :options="options"
            clearable
            placeholder="全部业务"
            @update:value="search"
          />
        </NFormItem>
        <NFormItem label="检索内容">
          <NInput v-model:value="keyword" clearable placeholder="文件名 / 业务名称 / 单号" @keyup.enter="search" />
        </NFormItem>
      </template>
    </HPageDataTable>
    <NModal
      :show="previewOpen"
      preset="card"
      title="历史原件查看"
      style="width: min(900px, calc(100vw - 40px))"
      @update:show="onPreviewShow"
    >
      <div v-if="previewing" role="status">正在读取原件…</div>
      <NAlert v-if="previewNotice" type="info">{{ previewNotice }}</NAlert>
      <template v-if="preview">
        <p>文件名称：{{ preview.name }}</p>
        <p>文件大小：{{ preview.size }} 字节</p>
        <pre v-if="!previewNotice" class="original-history-text">{{ preview.text }}</pre>
      </template>
      <template #footer>
        <NButton @click="closePreview">关闭</NButton>
      </template>
    </NModal>
  </section>
</template>
<script setup lang="ts">
import { computed, h, nextTick, ref, watch } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { NButton, NModal, NTooltip, type DataTableColumns } from 'naive-ui';
import HPageDataTable from '@/components/data/table/PageDataTable.vue';
import { saveAs } from 'file-saver';
import { formatLocalDateValue } from '@/utils';
import { originalHistoryApi, originalBusinessNames, type OriginalHistoryItem } from '../api/originalHistory';
const props = defineProps<{ businessType?: string; businessId?: string }>();
const selectedType = ref<string | null>(null);
const keyword = ref('');
const items = ref<OriginalHistoryItem[]>([]);
const tableRef = ref<InstanceType<typeof HPageDataTable>>();
const tableVersion = ref(0);
const queryParams = computed(() => ({
  businessType: props.businessType || selectedType.value,
  businessId: props.businessId,
  keyword: keyword.value.trim() || undefined,
}));
const downloading = ref<string | null>(null);
const previewing = ref<string | null>(null);
const previewOpen = ref(false);
const previewNotice = ref('');
const maximumPreviewBytes = 2 * 1024 * 1024;
const preview = ref<{ name: string; size: number; text: string }>();
let previewVersion = 0;
let previewAbort: AbortController | undefined;
const isTextOriginal = (name: string) => /\.(txt|json)$/i.test(name.trim());
const error = ref('');
let version = 0;
let pageAbort: AbortController | undefined;
let downloadAbort: AbortController | undefined;
const options = Object.entries(originalBusinessNames).map(([value, label]) => ({ value, label }));
const availability = (row: OriginalHistoryItem) =>
  ({
    AVAILABLE: '可查阅',
    REMOTE_VERSION_RECORDED: '按留存远端版本查阅',
    RECORDED_LOCATION: '按远端留存位置查阅',
    IDENTITY_UNVERIFIED: '文件身份待核实',
  })[row.availability] || '文件状态待核实';
const columns = computed<DataTableColumns<OriginalHistoryItem>>(() => [
  { title: '原件名称', key: 'name', width: 220, resizable: true, minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '关联业务',
    key: 'businessType',
    minWidth: 120,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 160,
    render: (row) => originalBusinessNames[row.businessType] || '其他业务材料',
  },
  { title: '留存单位', key: 'orgName', width: 180, resizable: true, minWidth: 150, ellipsis: { tooltip: true } },
  {
    title: '业务记录', key: 'businessLabel', width: 260, minWidth: 200, resizable: true,
    render: (row) => {
      const label = row.businessLabel?.trim() || '历史业务待核实';
      return h(NTooltip, { placement: 'top', style: { maxWidth: '360px', overflowWrap: 'anywhere' } }, {
        trigger: () => h('span', { class: 'original-history-business' }, label),
        default: () => [h('div', label), h('div', `内部编号：${row.businessId}`)],
      });
    },
  },
  {
    title: '留存时间',
    key: 'retainedAt',
    minWidth: 160,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 190,
    render: (row) => formatLocalDateValue(row.retainedAt, 'YYYY-MM-DD HH:mm:ss'),
  },
  { title: '读取状态', key: 'availability', minWidth: 140, resizable: true, ellipsis: { tooltip: true }, width: 180, render: availability },
  {
    title: '内容核验',
    key: 'contentIntegrity',
    minWidth: 140,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 180,
    render: (row) => (row.contentIntegrity === 'SHA256_RECORDED' ? '下载前核验留存内容' : '未留存内容基线'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row) => {
      const downloadButton = h(
        NButton,
        {
          text: true,
          type: 'primary',
          disabled:
            !eligible.value ||
            !!downloading.value ||
            !!previewing.value ||
            !['AVAILABLE', 'REMOTE_VERSION_RECORDED', 'RECORDED_LOCATION'].includes(row.availability),
          loading: downloading.value === row.id,
          onClick: () => download(row),
        },
        () => '下载原件',
      );
      if (!isTextOriginal(row.name)) return downloadButton;
      return h('div', { class: 'original-history-actions' }, [
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            disabled:
              !eligible.value ||
              !!downloading.value ||
              !!previewing.value ||
              !['AVAILABLE', 'REMOTE_VERSION_RECORDED', 'RECORDED_LOCATION'].includes(row.availability),
            loading: previewing.value === row.id,
            onClick: () => viewOriginal(row),
          },
          () => '查看原件',
        ),
        downloadButton,
      ]);
    },
  },
]);
function cancelRequests() {
  version++;
  pageAbort?.abort();
  downloadAbort?.abort();
  pageAbort = undefined;
  downloadAbort = undefined;
  closePreview();
  items.value = [];
  downloading.value = null;
  error.value = '';
}
function invalidate() {
  cancelRequests();
  // 业务或权限变化时重建表格，清除其旧数据、分页和在途结果。
  tableVersion.value++;
}
async function fetchHistory(params: Parameters<typeof originalHistoryApi.page>[0]) {
  if (!eligible.value) return null;
  cancelRequests();
  const current = version;
  pageAbort = new AbortController();
  try {
    const response = await originalHistoryApi.page(params, pageAbort.signal);
    if (current !== version) return null;
    items.value = response.data?.list || [];
    return response;
  } catch {
    if (current !== version) return null;
    error.value = '当前无法读取历史材料，请重新核对权限和原件。不能据空列表认定没有历史材料。';
    return { data: { list: [], total: 0 } };
  } finally {
    if (current === version) pageAbort = undefined;
  }
}
function search() {
  if (eligible.value) tableRef.value?.handleQuery();
}
async function reload() {
  const current = version;
  await nextTick();
  if (current === version) search();
}
async function download(row: OriginalHistoryItem) {
  const currentRow = items.value.find((item) => item.id === row.id);
  if (
    !eligible.value ||
    downloading.value ||
    previewing.value ||
    !currentRow ||
    !['AVAILABLE', 'REMOTE_VERSION_RECORDED', 'RECORDED_LOCATION'].includes(row.availability) ||
    !['AVAILABLE', 'REMOTE_VERSION_RECORDED', 'RECORDED_LOCATION'].includes(currentRow.availability)
  )
    return;
  const current = version;
  downloading.value = row.id;
  error.value = '';
  downloadAbort = new AbortController();
  try {
    const blob = await originalHistoryApi.download(row.id, downloadAbort.signal);
    if (current !== version) return;
    if (!(blob instanceof Blob) || blob.type !== 'application/octet-stream') throw new Error('invalid_download');
    saveAs(blob, currentRow.name);
  } catch {
    if (current === version) {
      invalidate();
      error.value = '当前无法下载该历史原件，请重新核对权限、文件身份和存储位置。';
    }
  } finally {
    if (current === version) {
      downloading.value = null;
      downloadAbort = undefined;
    }
  }
}
function closePreview() {
  previewVersion++;
  previewAbort?.abort();
  previewAbort = undefined;
  previewing.value = null;
  preview.value = undefined;
  previewNotice.value = '';
  previewOpen.value = false;
}
function onPreviewShow(show: boolean) {
  if (!show) closePreview();
}
async function viewOriginal(row: OriginalHistoryItem) {
  const currentRow = items.value.find((item) => item.id === row.id);
  if (
    !eligible.value ||
    downloading.value ||
    previewing.value ||
    !currentRow ||
    !isTextOriginal(currentRow.name) ||
    !['AVAILABLE', 'REMOTE_VERSION_RECORDED', 'RECORDED_LOCATION'].includes(row.availability) ||
    !['AVAILABLE', 'REMOTE_VERSION_RECORDED', 'RECORDED_LOCATION'].includes(currentRow.availability)
  )
    return;
  closePreview();
  const current = version;
  const ticket = previewVersion;
  const controller = new AbortController();
  previewAbort = controller;
  previewing.value = row.id;
  previewOpen.value = true;
  error.value = '';
  const valid = () => current === version && ticket === previewVersion && eligible.value && !controller.signal.aborted;
  try {
    const blob = await originalHistoryApi.download(row.id, controller.signal);
    if (!valid()) return;
    if (!(blob instanceof Blob) || blob.type !== 'application/octet-stream') throw new Error('invalid_download');
    if (blob.size > maximumPreviewBytes) {
      preview.value = { name: currentRow.name, size: blob.size, text: '' };
      previewNotice.value = '该原件超过 2 MiB，请关闭查看窗口后使用“下载原件”。';
      return;
    }
    const text = await blob.text();
    if (!valid()) return;
    preview.value = { name: currentRow.name, size: blob.size, text };
  } catch {
    if (valid()) {
      invalidate();
      error.value = '当前无法查看该历史原件，请重新核对权限、文件身份和存储位置。';
    }
  } finally {
    if (current === version && ticket === previewVersion) {
      previewing.value = null;
      previewAbort = undefined;
    }
  }
}
watch(
  () => [props.businessType, props.businessId],
  () => {
    selectedType.value = null;
    keyword.value = '';
  },
  { flush: 'sync' },
);
const { eligible } = useCurrentImageLifecycle(
  () => [props.businessType, props.businessId],
  invalidate,
  () => void reload(),
);
</script>

<style scoped>
.original-history-business { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.original-history-workspace {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
}
.original-history-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.original-history-text {
  max-height: 65vh;
  overflow: auto;
  font-family: inherit;
  font-weight: 400;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.original-history-table {
  flex: 1;
  height: auto;
  min-height: 260px;
}
</style>
